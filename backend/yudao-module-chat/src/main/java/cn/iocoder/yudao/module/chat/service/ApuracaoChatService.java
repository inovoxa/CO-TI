package cn.iocoder.yudao.module.chat.service;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.system.service.auth.EventosSeguranca;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Apuração das conversas do chat, para sindicância ou processo. Só leitura.
 *
 * <ul>
 *   <li>A permissão {@value #PERMISSAO} precisa estar num perfil da pessoa: o super administrador não passa só por ser
 *       administrador (ninguém recebe esse acesso sem querer).</li>
 *   <li>Toda consulta exige um motivo e vai para a trilha de auditoria encadeada, com os filtros usados.</li>
 *   <li>Mostra tudo, inclusive o que a pessoa apagou ou editou (o texto anterior fica em {@code chat.mensagem_historico}),
 *       mensagens anteriores à entrada no grupo e conversas de quem já saiu.</li>
 * </ul>
 */
@Service
public class ApuracaoChatService {

    public static final String PERMISSAO = "chat:apurar";
    private static final ErrorCode SEM_PERMISSAO = new ErrorCode(1_108_000_020,
            "A apuração do chat exige a permissão \"Apurar conversas do chat\" num perfil seu. Ser administrador não basta: "
                    + "peça que ela seja dada a um perfil, em Sistema → Perfis.");
    private static final ErrorCode INVALIDO = new ErrorCode(1_108_000_021, "{}");
    private static final int POR_PAGINA = 100;
    private static final int LIMITE_EXPORTACAO = 20_000;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private EventosSeguranca auditoria;

    @Value("${coti.chat.pasta-arquivos:arquivos/chat}")
    private String pastaArquivos;

    @Data
    public static class Filtro {
        @NotBlank(message = "Informe o motivo da apuração")
        @Size(min = 10, max = 500, message = "O motivo precisa ter entre 10 e 500 caracteres (ex.: o número do processo)")
        private String motivo;
        private Long pessoaId;
        /** Com pessoa: só o que ela enviou (sem isso, todas as mensagens das conversas de que ela participou) */
        private boolean somenteEnviadas;
        private Long conversaId;
        private LocalDate inicio;
        private LocalDate fim;
        private String texto;
        private int pagina = 1;
    }

    public record Pessoa(Long id, String nome, String usuario) {
    }

    public record Conversa(Long id, String tipo, String nome, List<String> participantes, long mensagens, LocalDateTime ultima) {
    }

    public record Versao(String acao, String conteudo, LocalDateTime quando, String por) {
    }

    public record Anexo(Long id, String nome, long tamanho, boolean expirado) {
    }

    public record Mensagem(Long id, Long conversaId, String conversa, String remetente, String usuario, String tipo,
                           String conteudo, LocalDateTime enviadaEm, LocalDateTime editadaEm, LocalDateTime apagadaEm,
                           List<Versao> versoes, List<Anexo> anexos) {
    }

    public record Pagina(List<Mensagem> mensagens, long total, int pagina, int porPagina) {
    }

    public record Arquivo(String nome, String tipoMime, Path caminho) {
    }

    // ------------------------------------------------------------------ consultas

    public boolean podeApurar(Long userId) {
        Boolean pode = jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM system_user_role ur
                    JOIN system_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 0
                    JOIN system_role_menu rm ON rm.role_id = ur.role_id AND rm.deleted = 0
                    JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 0
                    WHERE ur.user_id = ? AND ur.deleted = 0 AND m.permission = ?)""", Boolean.class, userId, PERMISSAO);
        return Boolean.TRUE.equals(pode);
    }

    /** Para escolher a pessoa; inclui quem está desativado. Não vai para a trilha: só lista nomes. */
    public List<Pessoa> pessoas(String busca, Long userId) {
        exigir(userId);
        String q = "%" + Optional.ofNullable(busca).orElse("").trim() + "%";
        return jdbcTemplate.query("""
                        SELECT id, nickname, username FROM system_users
                        WHERE deleted = 0 AND (itsm.sem_acento(nickname) ILIKE itsm.sem_acento(?) OR username ILIKE ?)
                        ORDER BY nickname LIMIT 30""",
                (rs, i) -> new Pessoa(rs.getLong(1), rs.getString(2), rs.getString(3)), q, q);
    }

    /** Conversas de uma pessoa (ou todas), com quantas mensagens têm no período. */
    public List<Conversa> conversas(Filtro f, Long userId) {
        exigir(userId);
        Periodo p = periodo(f);
        List<Object> args = new ArrayList<>(List.of(p.inicio(), p.fim()));
        String porPessoa = "";
        if (f.getPessoaId() != null) {
            porPessoa = " AND EXISTS (SELECT 1 FROM chat.participante pp WHERE pp.conversa_id = c.id AND pp.user_id = ?)";
            args.add(f.getPessoaId());
        }
        List<Conversa> r = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT c.id, c.tipo, c.nome, count(m.id) AS n, max(m.create_time) AS ultima
                FROM chat.conversa c
                LEFT JOIN chat.mensagem m ON m.conversa_id = c.id AND m.deleted = 0 AND m.create_time >= ? AND m.create_time < ?
                WHERE c.deleted = 0""" + porPessoa + """
                 GROUP BY c.id, c.tipo, c.nome HAVING count(m.id) > 0 ORDER BY max(m.create_time) DESC LIMIT 500""", rs -> {
            Timestamp u = rs.getTimestamp("ultima");
            r.add(new Conversa(rs.getLong("id"), rs.getString("tipo"), rs.getString("nome"), List.of(), rs.getLong("n"),
                    u == null ? null : u.toLocalDateTime()));
        }, args.toArray());
        Map<Long, List<String>> nomes = participantes(r.stream().map(Conversa::id).toList());
        List<Conversa> comNomes = r.stream().map(c -> new Conversa(c.id(), c.tipo(), rotulo(c.tipo(), c.nome(), nomes.get(c.id())),
                nomes.getOrDefault(c.id(), List.of()), c.mensagens(), c.ultima())).toList();
        registrar("chat.apuracao_conversas", userId, f, Map.of("conversas", comNomes.size()));
        return comNomes;
    }

    public Pagina mensagens(Filtro f, Long userId) {
        exigir(userId);
        Consulta q = consulta(f);
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM chat.mensagem m" + q.where(), Long.class, q.args().toArray());
        int pagina = Math.max(1, f.getPagina());
        List<Mensagem> lista = buscar(q, POR_PAGINA, (pagina - 1) * POR_PAGINA);
        registrar("chat.apuracao_consulta", userId, f, Map.of("pagina", pagina, "encontradas", total == null ? 0 : total));
        return new Pagina(lista, total == null ? 0 : total, pagina, POR_PAGINA);
    }

    /** Planilha com tudo o que os filtros acham (até {@value #LIMITE_EXPORTACAO} mensagens). */
    public List<Planilha.Aba> exportar(Filtro f, Long userId) {
        exigir(userId);
        Consulta q = consulta(f);
        List<Mensagem> lista = buscar(q, LIMITE_EXPORTACAO, 0);
        List<List<Object>> linhas = new ArrayList<>();
        for (Mensagem m : lista) {
            linhas.add(Arrays.asList(Planilha.dataHora(m.enviadaEm()), m.conversa(), m.remetente(), m.usuario(),
                    m.apagadaEm() != null ? "Apagada em " + Planilha.dataHora(m.apagadaEm())
                            : m.editadaEm() != null ? "Editada em " + Planilha.dataHora(m.editadaEm()) : "",
                    m.conteudo(),
                    String.join("\n", m.versoes().stream().map(v -> (v.acao().equals("apagada") ? "Antes de apagar" : "Antes de editar")
                            + " (" + Planilha.dataHora(v.quando()) + "): " + v.conteudo()).toList()),
                    String.join("\n", m.anexos().stream().map(a -> a.nome() + (a.expirado() ? " (expirado)" : "")).toList())));
        }
        List<List<Object>> registro = List.of(
                Arrays.asList("Motivo", f.getMotivo().strip()),
                Arrays.asList("Período", periodoTexto(f)),
                Arrays.asList("Mensagens", lista.size() >= LIMITE_EXPORTACAO ? "as primeiras " + LIMITE_EXPORTACAO : String.valueOf(lista.size())),
                Arrays.asList("Gerado em", Planilha.dataHora(LocalDateTime.now())));
        registrar("chat.apuracao_exportacao", userId, f, Map.of("mensagens", lista.size()));
        return List.of(new Planilha.Aba("Mensagens", List.of("Enviada em", "Conversa", "Remetente", "Usuário", "Situação", "Texto atual",
                        "Versões anteriores", "Anexos"), linhas),
                new Planilha.Aba("Apuração", List.of("Campo", "Valor"), registro));
    }

    /** Baixa um anexo que ainda não expirou. Também vai para a trilha. */
    public Arquivo anexo(Long anexoId, String motivo, Long userId) {
        exigir(userId);
        if (motivo == null || motivo.strip().length() < 10) {
            throw exception(INVALIDO, "Informe o motivo da apuração");
        }
        List<Map<String, Object>> l = jdbcTemplate.queryForList(
                "SELECT nome, tipo_mime, caminho, expirado_em, conversa_id, mensagem_id FROM chat.anexo WHERE id = ? AND deleted = 0", anexoId);
        if (l.isEmpty()) {
            throw exception(INVALIDO, "Anexo não encontrado");
        }
        Map<String, Object> a = l.get(0);
        if (a.get("expirado_em") != null) {
            throw exception(INVALIDO, "Esse anexo expirou e o arquivo já foi apagado do disco");
        }
        Path base = Path.of(pastaArquivos).toAbsolutePath().normalize();
        Path arquivo = base.resolve((String) a.get("caminho")).normalize();
        if (!arquivo.startsWith(base) || !Files.exists(arquivo)) {
            throw exception(INVALIDO, "O arquivo não está mais no disco");
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("motivo", motivo.strip());
        d.put("anexo", anexoId);
        d.put("nome", a.get("nome"));
        d.put("conversa", a.get("conversa_id"));
        auditoria.registrar("chat.apuracao_anexo", userId, login(userId), d);
        return new Arquivo((String) a.get("nome"), (String) a.get("tipo_mime"), arquivo);
    }

    // ------------------------------------------------------------------ interno

    private record Periodo(Timestamp inicio, Timestamp fim) {
    }

    record Consulta(String where, List<Object> args) {
    }

    private void exigir(Long userId) {
        if (userId == null || !podeApurar(userId)) {
            throw exception(SEM_PERMISSAO);
        }
    }

    private static Periodo periodo(Filtro f) {
        LocalDate inicio = f.getInicio() != null ? f.getInicio() : LocalDate.of(2000, 1, 1);
        LocalDate fim = f.getFim() != null ? f.getFim() : LocalDate.now();
        if (fim.isBefore(inicio)) {
            throw exception(INVALIDO, "O fim do período é antes do início");
        }
        return new Periodo(Timestamp.valueOf(inicio.atStartOfDay()), Timestamp.valueOf(fim.plusDays(1).atStartOfDay()));
    }

    private static String periodoTexto(Filtro f) {
        return (f.getInicio() == null ? "desde o início" : "de " + f.getInicio()) + " " + (f.getFim() == null ? "até hoje" : "até " + f.getFim());
    }

    /** Filtros montados só com marcadores; os valores vão como parâmetros. */
    static Consulta consulta(Filtro f) {
        if (f.getPessoaId() == null && f.getConversaId() == null && (f.getTexto() == null || f.getTexto().isBlank())) {
            throw exception(INVALIDO, "Escolha uma pessoa, uma conversa ou um texto para procurar");
        }
        Periodo p = periodo(f);
        StringBuilder w = new StringBuilder(" WHERE m.deleted = 0 AND m.create_time >= ? AND m.create_time < ?");
        List<Object> args = new ArrayList<>(List.of(p.inicio(), p.fim()));
        if (f.getConversaId() != null) {
            w.append(" AND m.conversa_id = ?");
            args.add(f.getConversaId());
        }
        if (f.getPessoaId() != null) {
            if (f.isSomenteEnviadas()) {
                w.append(" AND m.remetente_id = ?");
            } else {
                w.append(" AND EXISTS (SELECT 1 FROM chat.participante pp WHERE pp.conversa_id = m.conversa_id AND pp.user_id = ?)");
            }
            args.add(f.getPessoaId());
        }
        if (f.getTexto() != null && !f.getTexto().isBlank()) {
            String t = "%" + f.getTexto().strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (itsm.sem_acento(m.conteudo) ILIKE itsm.sem_acento(?) OR EXISTS (SELECT 1 FROM chat.mensagem_historico h "
                    + "WHERE h.mensagem_id = m.id AND itsm.sem_acento(h.conteudo) ILIKE itsm.sem_acento(?)))");
            args.add(t);
            args.add(t);
        }
        return new Consulta(w.toString(), args);
    }

    private List<Mensagem> buscar(Consulta q, int limite, int deslocamento) {
        List<Object> args = new ArrayList<>(q.args());
        args.add(limite);
        args.add(deslocamento);
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
                SELECT m.id, m.conversa_id, m.tipo, m.conteudo, m.create_time, m.editada_em, m.apagada_em,
                       c.tipo AS conversa_tipo, c.nome AS conversa_nome, u.nickname, u.username
                FROM chat.mensagem m
                JOIN chat.conversa c ON c.id = m.conversa_id
                LEFT JOIN system_users u ON u.id = m.remetente_id""" + q.where() + " ORDER BY m.create_time, m.id LIMIT ? OFFSET ?",
                args.toArray());
        if (linhas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = linhas.stream().map(l -> ((Number) l.get("id")).longValue()).toList();
        Map<Long, List<Versao>> versoes = new HashMap<>();
        jdbcTemplate.query("""
                SELECT h.mensagem_id, h.acao, h.conteudo, h.quando, u.nickname FROM chat.mensagem_historico h
                LEFT JOIN system_users u ON u.id = h.user_id
                WHERE h.mensagem_id = ANY(?::int8[]) ORDER BY h.quando""", rs -> {
            versoes.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new Versao(rs.getString(2), rs.getString(3),
                    rs.getTimestamp(4).toLocalDateTime(), rs.getString(5)));
        }, arrayDeIds(ids));
        Map<Long, List<Anexo>> anexos = new HashMap<>();
        jdbcTemplate.query("SELECT mensagem_id, id, nome, tamanho, expirado_em FROM chat.anexo WHERE mensagem_id = ANY(?::int8[]) AND deleted = 0 "
                + "ORDER BY id", rs -> {
            anexos.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new Anexo(rs.getLong(2), rs.getString(3), rs.getLong(4),
                    rs.getTimestamp(5) != null));
        }, arrayDeIds(ids));
        Map<Long, List<String>> nomes = participantes(linhas.stream().map(l -> ((Number) l.get("conversa_id")).longValue()).distinct().toList());
        List<Mensagem> r = new ArrayList<>();
        for (Map<String, Object> l : linhas) {
            long id = ((Number) l.get("id")).longValue();
            long conversaId = ((Number) l.get("conversa_id")).longValue();
            r.add(new Mensagem(id, conversaId, rotulo((String) l.get("conversa_tipo"), (String) l.get("conversa_nome"), nomes.get(conversaId)),
                    (String) l.get("nickname"), (String) l.get("username"), (String) l.get("tipo"), (String) l.get("conteudo"),
                    data(l.get("create_time")), data(l.get("editada_em")), data(l.get("apagada_em")),
                    versoes.getOrDefault(id, List.of()), anexos.getOrDefault(id, List.of())));
        }
        return r;
    }

    /** Nomes de quem participa ou participou (inclui quem saiu do grupo). */
    private Map<Long, List<String>> participantes(List<Long> conversas) {
        Map<Long, List<String>> r = new HashMap<>();
        if (conversas.isEmpty()) {
            return r;
        }
        jdbcTemplate.query("""
                SELECT p.conversa_id, u.nickname, p.deleted FROM chat.participante p JOIN system_users u ON u.id = p.user_id
                WHERE p.conversa_id = ANY(?::int8[]) ORDER BY u.nickname""", rs -> {
            r.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2) + (rs.getInt(3) != 0 ? " (saiu)" : ""));
        }, arrayDeIds(conversas));
        return r;
    }

    private static String rotulo(String tipo, String nome, List<String> participantes) {
        if ("grupo".equals(tipo)) {
            return "Grupo " + (nome == null ? "" : nome);
        }
        return participantes == null ? "Conversa direta" : String.join(" e ", participantes);
    }

    /** Literal de array do PostgreSQL ("{1,2,3}"), usado em "= ANY(?::int8[])". Só números: nada vindo de texto livre. */
    static String arrayDeIds(List<Long> ids) {
        StringJoiner j = new StringJoiner(",", "{", "}");
        ids.forEach(id -> j.add(String.valueOf(id.longValue())));
        return j.toString();
    }

    private static LocalDateTime data(Object o) {
        return o instanceof Timestamp t ? t.toLocalDateTime() : o instanceof LocalDateTime d ? d : null;
    }

    private String login(Long userId) {
        List<String> l = jdbcTemplate.queryForList("SELECT username FROM system_users WHERE id = ?", String.class, userId);
        return l.isEmpty() ? null : l.get(0);
    }

    private void registrar(String acao, Long userId, Filtro f, Map<String, Object> resultado) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("motivo", f.getMotivo().strip());
        if (f.getPessoaId() != null) {
            d.put("pessoa", f.getPessoaId());
            d.put("somenteEnviadas", f.isSomenteEnviadas());
        }
        if (f.getConversaId() != null) {
            d.put("conversa", f.getConversaId());
        }
        if (f.getTexto() != null && !f.getTexto().isBlank()) {
            d.put("texto", f.getTexto().strip());
        }
        d.put("periodo", periodoTexto(f));
        d.putAll(resultado);
        auditoria.registrar(acao, userId, login(userId), d);
    }

}
