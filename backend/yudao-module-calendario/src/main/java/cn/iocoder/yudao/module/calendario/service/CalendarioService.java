package cn.iocoder.yudao.module.calendario.service;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Calendário: aniversariantes e a agenda individual.
 *
 * <ul>
 *   <li>A data de nascimento é a própria pessoa quem informa, em Meu perfil. Para os outros só aparece se ela marcar como
 *       pública, e só dia e mês: o ano, que revela a idade, nunca sai daqui para outra pessoa.</li>
 *   <li>Cada compromisso tem um dono, o único que altera ou exclui. Ele pode convidar pessoas: o compromisso aparece
 *       na agenda delas, que respondem se vão ou saem dele. O resto da agenda do dono ninguém vê.</li>
 * </ul>
 */
@Service
public class CalendarioService {

    private static final ErrorCode INVALIDO = new ErrorCode(1_110_000_001, "{}");
    private static final ErrorCode NAO_ENCONTRADO = new ErrorCode(1_110_000_002, "Compromisso não encontrado");
    private static final int LIMITE_EVENTOS = 2_000;
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private AvisosAgenda avisos;

    // ---------- data de nascimento ----------

    /** Datas vão como texto "aaaa-mm-dd" (o Jackson do projeto mandaria LocalDate como lista [a, m, d]). */
    public record Nascimento(String data, boolean publico) {
    }

    @Data
    public static class NascimentoReq {
        /** Vazio apaga a data */
        private LocalDate data;
        private boolean publico;
    }

    public Nascimento meuNascimento(Long userId) {
        return jdbcTemplate.query("SELECT data, publico FROM calendario.nascimento WHERE usuario_id = ?",
                (rs, i) -> new Nascimento(rs.getObject("data", LocalDate.class).toString(), rs.getBoolean("publico")), userId)
                .stream().findFirst().orElse(new Nascimento(null, false));
    }

    public void salvarNascimento(Long userId, NascimentoReq req) {
        if (req.getData() == null) {
            jdbcTemplate.update("DELETE FROM calendario.nascimento WHERE usuario_id = ?", userId);
            return;
        }
        validarNascimento(req.getData(), LocalDate.now());
        jdbcTemplate.update("""
                INSERT INTO calendario.nascimento (usuario_id, data, publico, update_time) VALUES (?, ?, ?, now())
                ON CONFLICT (usuario_id) DO UPDATE SET data = EXCLUDED.data, publico = EXCLUDED.publico, update_time = now()
                """, userId, req.getData(), req.isPublico());
    }

    static void validarNascimento(LocalDate data, LocalDate hoje) {
        if (data.isAfter(hoje)) {
            throw exception(INVALIDO, "A data de nascimento não pode ser no futuro");
        }
        if (data.getYear() < 1900) {
            throw exception(INVALIDO, "Confira o ano da data de nascimento");
        }
    }

    /** Em que dia o aniversário cai no ano: quem nasceu em 29/02 comemora em 28/02 nos anos que não são bissextos. */
    static LocalDate diaNoAno(LocalDate nascimento, int ano) {
        return MonthDay.from(nascimento).atYear(ano);
    }

    // ---------- aniversariantes ----------

    /** {@code proprio}: é o aniversário de quem está vendo (aparece para ela mesmo se for privado). */
    public record Aniversariante(Long usuarioId, String nome, String unidade, String avatar, String dia, boolean publico,
                                 boolean proprio) {
    }

    public List<Aniversariante> aniversariantes(int ano, Integer mes, Long deptId, Long userId) {
        if (mes != null && (mes < 1 || mes > 12)) {
            throw exception(INVALIDO, "Mês inválido");
        }
        if (ano < 1900 || ano > 3000) {
            throw exception(INVALIDO, "Ano inválido");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.nickname, u.avatar, d.name AS unidade, n.data, n.publico
                FROM calendario.nascimento n
                JOIN system_users u ON u.id = n.usuario_id AND u.deleted = 0 AND u.status = 0
                LEFT JOIN system_dept d ON d.id = u.dept_id
                WHERE (n.publico OR n.usuario_id = ?)
                """);
        List<Object> args = new ArrayList<>(List.of(userId));
        if (mes != null) {
            sql.append(" AND extract(month FROM n.data) = ?");
            args.add(mes);
        }
        if (deptId != null) {
            // a unidade e as que ficam abaixo dela
            sql.append("""
                     AND u.dept_id IN (WITH RECURSIVE arvore AS (
                        SELECT id FROM system_dept WHERE id = ? AND deleted = 0
                        UNION SELECT s.id FROM system_dept s JOIN arvore a ON s.parent_id = a.id WHERE s.deleted = 0)
                      SELECT id FROM arvore)
                    """);
            args.add(deptId);
        }
        sql.append(" ORDER BY extract(month FROM n.data), extract(day FROM n.data), u.nickname");
        return jdbcTemplate.query(sql.toString(), (rs, i) -> {
            long id = rs.getLong("id");
            return new Aniversariante(id, rs.getString("nickname"), rs.getString("unidade"), rs.getString("avatar"),
                    diaNoAno(rs.getObject("data", LocalDate.class), ano).toString(), rs.getBoolean("publico"), id == userId);
        }, args.toArray());
    }

    public List<Planilha.Aba> exportarAniversariantes(int ano, Integer mes, Long deptId, Long userId) {
        List<List<Object>> linhas = new ArrayList<>();
        for (Aniversariante a : aniversariantes(ano, mes, deptId, userId)) {
            if (!a.publico()) {
                continue; // a data privada de quem exporta não vai para um arquivo
            }
            linhas.add(List.of(LocalDate.parse(a.dia()).format(DIA_MES), a.nome(), a.unidade() == null ? "" : a.unidade()));
        }
        return List.of(new Planilha.Aba("Aniversariantes", List.of("Dia", "Nome", "Unidade"), linhas));
    }

    // ---------- agenda: compromissos e convites ----------

    public static final String PENDENTE = "pendente";
    public static final Set<String> RESPOSTAS = Set.of("sim", "nao", "talvez");
    private static final int LIMITE_PARTICIPANTES = 50;
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    public record Participante(Long id, String nome, String resposta) {
    }

    /**
     * {@code meu}: quem está vendo é o dono (pode alterar e excluir). Senão é convidado: vê o compromisso, responde e
     * pode sair, mas não muda nada.
     */
    public record Evento(Long id, String titulo, String descricao, String local, LocalDateTime inicio, LocalDateTime fim,
                         boolean diaInteiro, String cor, boolean meu, Long donoId, String dono, String minhaResposta,
                         List<Participante> participantes) {
    }

    @Data
    public static class EventoReq {
        @NotBlank(message = "Informe o título")
        @Size(max = 120, message = "O título pode ter até 120 caracteres")
        private String titulo;
        @Size(max = 2000, message = "A descrição pode ter até 2000 caracteres")
        private String descricao;
        @Size(max = 200, message = "O local pode ter até 200 caracteres")
        private String local;
        @NotNull(message = "Informe quando começa")
        private LocalDateTime inicio;
        private LocalDateTime fim;
        private boolean diaInteiro;
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida")
        private String cor;
        /** Pessoas convidadas */
        @Size(max = LIMITE_PARTICIPANTES, message = "Convide até 50 pessoas")
        private List<Long> participantes = new ArrayList<>();
    }

    public record Pessoa(Long id, String nome, String unidade) {
    }

    /** Pessoas ativas para convidar (não traz quem está procurando nem o telão). */
    public List<Pessoa> pessoas(String busca, Long userId) {
        String b = "%" + (busca == null ? "" : busca.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        return jdbcTemplate.query("""
                SELECT u.id, u.nickname, d.name AS unidade FROM system_users u LEFT JOIN system_dept d ON d.id = u.dept_id
                WHERE u.deleted = 0 AND u.status = 0 AND u.id <> ?
                  AND (itsm.sem_acento(u.nickname) ILIKE itsm.sem_acento(?) OR u.username ILIKE ?)
                  AND NOT EXISTS (SELECT 1 FROM system_user_role ur JOIN system_role r ON r.id = ur.role_id
                                  WHERE ur.user_id = u.id AND ur.deleted = 0 AND r.code = 'telao')
                ORDER BY u.nickname LIMIT 30
                """, (rs, i) -> new Pessoa(rs.getLong("id"), rs.getString("nickname"), rs.getString("unidade")), userId, b, b);
    }

    private static final String SELECT_EVENTO = """
            SELECT e.id, e.titulo, e.descricao, e.local, e.inicio, e.fim, e.dia_inteiro, e.cor, e.dono_id, u.nickname AS dono,
                   c.resposta AS minha_resposta
            FROM calendario.evento e
            LEFT JOIN system_users u ON u.id = e.dono_id
            LEFT JOIN calendario.convite c ON c.evento_id = e.id AND c.usuario_id = ?
            """;

    public List<Evento> eventos(Long userId, LocalDate de, LocalDate ate) {
        if (de == null || ate == null || ate.isBefore(de)) {
            throw exception(INVALIDO, "Período inválido");
        }
        if (de.plusDays(400).isBefore(ate)) {
            throw exception(INVALIDO, "O período pode ter até 400 dias");
        }
        List<Linha> linhas = jdbcTemplate.query(SELECT_EVENTO + """
                WHERE (e.dono_id = ? OR c.usuario_id IS NOT NULL) AND e.inicio < ? AND COALESCE(e.fim, e.inicio) >= ?
                ORDER BY e.inicio, e.id LIMIT ?
                """, Linha.MAPPER, userId, userId, Timestamp.valueOf(ate.plusDays(1).atStartOfDay()),
                Timestamp.valueOf(de.atStartOfDay()), LIMITE_EVENTOS);
        return montar(linhas, userId);
    }

    /** Um compromisso do dono ou para o qual a pessoa foi convidada (aberto pelo aviso do sino). */
    public Evento evento(Long userId, Long id) {
        List<Linha> l = jdbcTemplate.query(SELECT_EVENTO + " WHERE e.id = ? AND (e.dono_id = ? OR c.usuario_id IS NOT NULL)",
                Linha.MAPPER, userId, id, userId);
        if (l.isEmpty()) {
            throw exception(NAO_ENCONTRADO);
        }
        return montar(l, userId).get(0);
    }

    private record Linha(Long id, String titulo, String descricao, String local, LocalDateTime inicio, LocalDateTime fim,
                         boolean diaInteiro, String cor, Long donoId, String dono, String minhaResposta) {
        static final RowMapper<Linha> MAPPER = (rs, i) -> new Linha(rs.getLong("id"),
                rs.getString("titulo"), rs.getString("descricao"), rs.getString("local"),
                rs.getObject("inicio", LocalDateTime.class), rs.getObject("fim", LocalDateTime.class),
                rs.getBoolean("dia_inteiro"), rs.getString("cor"), rs.getLong("dono_id"), rs.getString("dono"),
                rs.getString("minha_resposta"));
    }

    private List<Evento> montar(List<Linha> linhas, Long userId) {
        Map<Long, List<Participante>> participantes = new HashMap<>();
        if (!linhas.isEmpty()) {
            jdbcTemplate.query("""
                    SELECT c.evento_id, c.usuario_id, u.nickname, c.resposta FROM calendario.convite c
                    JOIN system_users u ON u.id = c.usuario_id
                    WHERE c.evento_id = ANY(?::int8[]) ORDER BY u.nickname
                    """, (RowCallbackHandler) rs -> participantes.computeIfAbsent(rs.getLong("evento_id"), k -> new ArrayList<>())
                    .add(new Participante(rs.getLong("usuario_id"), rs.getString("nickname"), rs.getString("resposta"))),
                    arrayPg(linhas.stream().map(Linha::id).toList()));
        }
        return linhas.stream().map(l -> {
            boolean meu = l.donoId().equals(userId);
            return new Evento(l.id(), l.titulo(), l.descricao(), l.local(), l.inicio(), l.fim(), l.diaInteiro(), l.cor(), meu,
                    l.donoId(), l.dono(), meu ? null : l.minhaResposta(), participantes.getOrDefault(l.id(), List.of()));
        }).toList();
    }

    /** Lista de ids como literal de array do PostgreSQL, para {@code = ANY(?::int8[])}. */
    private static String arrayPg(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    /** Dia inteiro guarda do começo do primeiro dia ao começo do último; o fim, se vier, não pode ser antes do início. */
    static EventoReq normalizar(EventoReq req) {
        req.setTitulo(req.getTitulo().trim());
        req.setDescricao(vazioNulo(req.getDescricao()));
        req.setLocal(vazioNulo(req.getLocal()));
        if (req.isDiaInteiro()) {
            req.setInicio(req.getInicio().toLocalDate().atStartOfDay());
            if (req.getFim() != null) {
                req.setFim(req.getFim().toLocalDate().atStartOfDay());
            }
        }
        if (req.getFim() != null && req.getFim().isBefore(req.getInicio())) {
            throw exception(INVALIDO, "O fim não pode ser antes do início");
        }
        if (req.getParticipantes() == null) {
            req.setParticipantes(new ArrayList<>());
        }
        return req;
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Convidados válidos: pessoas ativas, sem repetir e sem o próprio dono. */
    private Set<Long> convidaveis(List<Long> pedidos, Long donoId) {
        Set<Long> ids = new LinkedHashSet<>(pedidos);
        ids.remove(null);
        ids.remove(donoId);
        if (ids.isEmpty()) {
            return ids;
        }
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT id FROM system_users WHERE id = ANY(?::int8[]) AND deleted = 0 AND status = 0", Long.class, arrayPg(ids)));
    }

    @Transactional
    public Long criar(Long userId, EventoReq req) {
        normalizar(req);
        Long id = jdbcTemplate.queryForObject("SELECT nextval('calendario.evento_seq')", Long.class);
        jdbcTemplate.update("""
                INSERT INTO calendario.evento (id, dono_id, titulo, descricao, local, inicio, fim, dia_inteiro, cor)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, userId, req.getTitulo(), req.getDescricao(), req.getLocal(), Timestamp.valueOf(req.getInicio()),
                req.getFim() == null ? null : Timestamp.valueOf(req.getFim()), req.isDiaInteiro(), req.getCor());
        Set<Long> convidados = convidaveis(req.getParticipantes(), userId);
        convidados.forEach(p -> inserirConvite(id, p));
        avisos.avisar(convidados, AvisosAgenda.CONVITE, userId, id, req.getTitulo(), quando(req.getInicio(), req.isDiaInteiro()));
        return id;
    }

    private void inserirConvite(Long eventoId, Long usuarioId) {
        jdbcTemplate.update("INSERT INTO calendario.convite (evento_id, usuario_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                eventoId, usuarioId);
    }

    @Transactional
    public void alterar(Long userId, Long id, EventoReq req) {
        normalizar(req);
        List<Map<String, Object>> antes = jdbcTemplate.queryForList(
                "SELECT titulo, local, inicio, fim, dia_inteiro FROM calendario.evento WHERE id = ? AND dono_id = ? FOR UPDATE",
                id, userId);
        if (antes.isEmpty()) {
            throw exception(NAO_ENCONTRADO);
        }
        jdbcTemplate.update("""
                UPDATE calendario.evento SET titulo = ?, descricao = ?, local = ?, inicio = ?, fim = ?, dia_inteiro = ?, cor = ?,
                    update_time = now()
                WHERE id = ? AND dono_id = ?
                """, req.getTitulo(), req.getDescricao(), req.getLocal(), Timestamp.valueOf(req.getInicio()),
                req.getFim() == null ? null : Timestamp.valueOf(req.getFim()), req.isDiaInteiro(), req.getCor(), id, userId);

        Set<Long> atuais = new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT usuario_id FROM calendario.convite WHERE evento_id = ?", Long.class, id));
        Set<Long> desejados = convidaveis(req.getParticipantes(), userId);
        Set<Long> novos = new LinkedHashSet<>(desejados);
        novos.removeAll(atuais);
        Set<Long> retirados = new LinkedHashSet<>(atuais);
        retirados.removeAll(desejados);
        Set<Long> mantidos = new LinkedHashSet<>(atuais);
        mantidos.retainAll(desejados);

        retirados.forEach(p -> jdbcTemplate.update("DELETE FROM calendario.convite WHERE evento_id = ? AND usuario_id = ?", id, p));
        novos.forEach(p -> inserirConvite(id, p));

        String quando = quando(req.getInicio(), req.isDiaInteiro());
        avisos.avisar(novos, AvisosAgenda.CONVITE, userId, id, req.getTitulo(), quando);
        avisos.avisar(retirados, AvisosAgenda.RETIRADO, userId, null, req.getTitulo(), quando);
        Map<String, Object> a = antes.get(0);
        boolean horario = mudouHorario(paraData(a.get("inicio")), paraData(a.get("fim")), Boolean.TRUE.equals(a.get("dia_inteiro")), req);
        if (horario || !Objects.equals(a.get("titulo"), req.getTitulo()) || !Objects.equals(a.get("local"), req.getLocal())) {
            if (horario && !mantidos.isEmpty()) {
                // mudou o dia ou a hora: quem já tinha respondido confirma de novo
                jdbcTemplate.update("UPDATE calendario.convite SET resposta = ?, respondido_em = NULL WHERE evento_id = ?",
                        PENDENTE, id);
            }
            avisos.avisar(mantidos, AvisosAgenda.ALTERADO, userId, id, req.getTitulo(), quando);
        }
    }

    /** Dia, hora ou "dia inteiro" mudou (cor, descrição e local não pedem nova confirmação). */
    static boolean mudouHorario(LocalDateTime inicio, LocalDateTime fim, boolean diaInteiro, EventoReq req) {
        return !Objects.equals(inicio, req.getInicio()) || !Objects.equals(fim, req.getFim()) || diaInteiro != req.isDiaInteiro();
    }

    private static LocalDateTime paraData(Object o) {
        if (o instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        return (LocalDateTime) o;
    }

    @Transactional
    public void excluir(Long userId, Long id) {
        List<Map<String, Object>> ev = jdbcTemplate.queryForList(
                "SELECT titulo, inicio, dia_inteiro FROM calendario.evento WHERE id = ? AND dono_id = ?", id, userId);
        if (ev.isEmpty()) {
            throw exception(NAO_ENCONTRADO);
        }
        Set<Long> convidados = new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT usuario_id FROM calendario.convite WHERE evento_id = ?", Long.class, id));
        jdbcTemplate.update("DELETE FROM calendario.evento WHERE id = ? AND dono_id = ?", id, userId);
        Map<String, Object> e = ev.get(0);
        avisos.avisar(convidados, AvisosAgenda.CANCELADO, userId, null, (String) e.get("titulo"),
                quando(paraData(e.get("inicio")), Boolean.TRUE.equals(e.get("dia_inteiro"))));
    }

    /** Resposta do convidado: vai, não vai ou talvez. O dono recebe aviso. */
    @Transactional
    public void responder(Long userId, Long id, String resposta) {
        if (resposta == null || !RESPOSTAS.contains(resposta)) {
            throw exception(INVALIDO, "Resposta inválida");
        }
        if (jdbcTemplate.update("UPDATE calendario.convite SET resposta = ?, respondido_em = now() WHERE evento_id = ? AND usuario_id = ?",
                resposta, id, userId) == 0) {
            throw exception(NAO_ENCONTRADO);
        }
        avisarDono(userId, id, switch (resposta) {
            case "sim" -> "vai participar de";
            case "nao" -> "não vai participar de";
            default -> "talvez participe de";
        });
    }

    /** O convidado sai do compromisso: some da agenda dele e o dono fica sabendo. */
    @Transactional
    public void sair(Long userId, Long id) {
        if (jdbcTemplate.update("DELETE FROM calendario.convite WHERE evento_id = ? AND usuario_id = ?", id, userId) == 0) {
            throw exception(NAO_ENCONTRADO);
        }
        avisarDono(userId, id, "saiu de");
    }

    private void avisarDono(Long userId, Long id, String acao) {
        jdbcTemplate.query("SELECT dono_id, titulo, inicio, dia_inteiro FROM calendario.evento WHERE id = ?",
                (RowCallbackHandler) rs -> avisos.resposta(rs.getLong("dono_id"), userId, id, rs.getString("titulo"), acao,
                        quando(rs.getObject("inicio", LocalDateTime.class), rs.getBoolean("dia_inteiro"))), id);
    }

    static String quando(LocalDateTime inicio, boolean diaInteiro) {
        return diaInteiro ? inicio.format(DIA_MES) + " (dia inteiro)" : inicio.format(DIA_HORA);
    }

}
