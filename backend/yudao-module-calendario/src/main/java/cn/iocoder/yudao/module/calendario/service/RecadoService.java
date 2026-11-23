package cn.iocoder.yudao.module.calendario.service;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Recados no calendário: um texto que aparece num aviso ao entrar no sistema, do primeiro ao último dia do período.
 *
 * <ul>
 *   <li>Sem unidade é recado geral: todo mundo vê. Com unidades, vê quem está numa delas ou numa que fica abaixo.</li>
 *   <li>Quem dá "Ciente" não vê mais o aviso daquele recado ao entrar; ele continua no calendário.</li>
 *   <li>Quem publica (permissão {@code calendario:recado}) vê e altera todos, inclusive os de outras unidades.</li>
 * </ul>
 */
@Service
public class RecadoService {

    private static final ErrorCode INVALIDO = new ErrorCode(1_110_000_003, "{}");
    private static final ErrorCode NAO_ENCONTRADO = new ErrorCode(1_110_000_004, "Recado não encontrado");
    static final int DIAS_MAXIMOS = 366;
    private static final int LIMITE = 500;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** Datas como texto "aaaa-mm-dd" (o Jackson do projeto mandaria LocalDate como lista). */
    public record Recado(Long id, String titulo, String texto, String inicio, String fim, String cor, Long autorId,
                         String autor, List<Unidade> unidades, boolean ciente) {
    }

    public record Unidade(Long id, String nome) {
    }

    @Data
    public static class RecadoReq {
        @NotBlank(message = "Informe o título")
        @Size(max = 120, message = "O título pode ter até 120 caracteres")
        private String titulo;
        @NotBlank(message = "Escreva o recado")
        @Size(max = 4000, message = "O recado pode ter até 4000 caracteres")
        private String texto;
        @NotNull(message = "Informe o primeiro dia")
        private LocalDate inicio;
        /** Vazio: só o primeiro dia */
        private LocalDate fim;
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida")
        private String cor;
        /** Vazio: recado geral */
        @Size(max = 200, message = "Escolha até 200 unidades")
        private List<Long> unidades = new ArrayList<>();
    }

    // ------------------------------------------------------------------ consulta

    /**
     * Recados do período que a pessoa enxerga (quem publica enxerga todos).
     */
    public List<Recado> recados(Long userId, boolean publica, LocalDate de, LocalDate ate) {
        if (de == null || ate == null || ate.isBefore(de) || de.plusDays(400).isBefore(ate)) {
            throw exception(INVALIDO, "Período inválido (até 400 dias)");
        }
        return buscar(userId, publica, "r.inicio <= ? AND r.fim >= ?", List.of(ate, de));
    }

    /** O aviso de entrada: recados de hoje que a pessoa enxerga e ainda não deu "Ciente". */
    public List<Recado> pendentes(Long userId, LocalDate hoje) {
        return buscar(userId, false, "r.inicio <= ? AND r.fim >= ? AND NOT EXISTS (SELECT 1 FROM calendario.recado_ciente c "
                + "WHERE c.recado_id = r.id AND c.usuario_id = ?)", List.of(hoje, hoje, userId));
    }

    private List<Recado> buscar(Long userId, boolean todos, String filtro, List<Object> args) {
        List<Object> a = new ArrayList<>();
        a.add(userId);
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.titulo, r.texto, r.inicio, r.fim, r.cor, r.autor_id, u.nickname AS autor,
                       EXISTS (SELECT 1 FROM calendario.recado_ciente c WHERE c.recado_id = r.id AND c.usuario_id = ?) AS ciente
                FROM calendario.recado r
                LEFT JOIN system_users u ON u.id = r.autor_id
                WHERE\s""").append(filtro);
        a.addAll(args);
        if (!todos) {
            // geral, ou para a unidade da pessoa ou alguma acima dela na árvore
            sql.append("""
                     AND (NOT EXISTS (SELECT 1 FROM calendario.recado_unidade x WHERE x.recado_id = r.id)
                          OR EXISTS (SELECT 1 FROM calendario.recado_unidade x WHERE x.recado_id = r.id AND x.dept_id IN (
                              WITH RECURSIVE acima AS (
                                  SELECT d.id, d.parent_id FROM system_dept d
                                  JOIN system_users p ON p.dept_id = d.id WHERE p.id = ? AND d.deleted = 0
                                  UNION SELECT s.id, s.parent_id FROM system_dept s JOIN acima c ON s.id = c.parent_id
                                  WHERE s.deleted = 0)
                              SELECT id FROM acima)))
                    """);
            a.add(userId);
        }
        sql.append(" ORDER BY r.inicio, r.id LIMIT ").append(LIMITE);
        record Linha(Long id, String titulo, String texto, LocalDate inicio, LocalDate fim, String cor, Long autorId,
                     String autor, boolean ciente) {
        }
        List<Linha> linhas = jdbcTemplate.query(sql.toString(), (rs, i) -> new Linha(rs.getLong("id"), rs.getString("titulo"),
                rs.getString("texto"), rs.getObject("inicio", LocalDate.class), rs.getObject("fim", LocalDate.class),
                rs.getString("cor"), rs.getLong("autor_id"), rs.getString("autor"), rs.getBoolean("ciente")), a.toArray());
        Map<Long, List<Unidade>> unidades = new HashMap<>();
        if (!linhas.isEmpty()) {
            jdbcTemplate.query("""
                    SELECT x.recado_id, x.dept_id, d.name FROM calendario.recado_unidade x
                    LEFT JOIN system_dept d ON d.id = x.dept_id
                    WHERE x.recado_id = ANY(?::int8[]) ORDER BY d.name
                    """, (RowCallbackHandler) rs -> unidades.computeIfAbsent(rs.getLong("recado_id"), k -> new ArrayList<>())
                    .add(new Unidade(rs.getLong("dept_id"), rs.getString("name"))),
                    arrayPg(linhas.stream().map(Linha::id).toList()));
        }
        return linhas.stream().map(l -> new Recado(l.id(), l.titulo(), l.texto(), l.inicio().toString(), l.fim().toString(),
                l.cor(), l.autorId(), l.autor(), unidades.getOrDefault(l.id(), List.of()), l.ciente())).toList();
    }

    // ------------------------------------------------------------------ "ciente"

    /** Só marca recados que a pessoa enxerga (um id qualquer não vira registro). */
    public void ciente(Long userId, Collection<Long> ids) {
        Set<Long> pedidos = new LinkedHashSet<>(ids);
        pedidos.remove(null);
        if (pedidos.isEmpty()) {
            return;
        }
        LocalDate hoje = LocalDate.now();
        Set<Long> visiveis = buscar(userId, false, "r.id = ANY(?::int8[]) AND r.inicio <= ? AND r.fim >= ?",
                List.of(arrayPg(pedidos), hoje, hoje)).stream().map(Recado::id).collect(Collectors.toSet());
        for (Long id : visiveis) {
            jdbcTemplate.update("INSERT INTO calendario.recado_ciente (recado_id, usuario_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    id, userId);
        }
    }

    // ------------------------------------------------------------------ publicação

    /** Período válido e unidades que existem; texto sem espaços sobrando. */
    static RecadoReq normalizar(RecadoReq req) {
        req.setTitulo(req.getTitulo().trim());
        req.setTexto(req.getTexto().strip());
        if (req.getFim() == null) {
            req.setFim(req.getInicio());
        }
        if (req.getFim().isBefore(req.getInicio())) {
            throw exception(INVALIDO, "O último dia não pode ser antes do primeiro");
        }
        if (req.getInicio().plusDays(DIAS_MAXIMOS).isBefore(req.getFim())) {
            throw exception(INVALIDO, "O recado pode durar até " + DIAS_MAXIMOS + " dias");
        }
        if (req.getUnidades() == null) {
            req.setUnidades(new ArrayList<>());
        }
        return req;
    }

    private Set<Long> unidadesValidas(List<Long> pedidas) {
        Set<Long> ids = new LinkedHashSet<>(pedidas);
        ids.remove(null);
        if (ids.isEmpty()) {
            return ids;
        }
        Set<Long> existem = new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT id FROM system_dept WHERE id = ANY(?::int8[]) AND deleted = 0", Long.class, arrayPg(ids)));
        if (existem.size() != ids.size()) {
            throw exception(INVALIDO, "Alguma unidade escolhida não existe mais; recarregue a página");
        }
        return existem;
    }

    @Transactional
    public Long criar(Long userId, RecadoReq req) {
        normalizar(req);
        Set<Long> unidades = unidadesValidas(req.getUnidades());
        Long id = jdbcTemplate.queryForObject("SELECT nextval('calendario.recado_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO calendario.recado (id, autor_id, titulo, texto, inicio, fim, cor) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, userId, req.getTitulo(), req.getTexto(), req.getInicio(), req.getFim(), req.getCor());
        unidades.forEach(d -> jdbcTemplate.update("INSERT INTO calendario.recado_unidade (recado_id, dept_id) VALUES (?, ?)", id, d));
        return id;
    }

    /** Mudou o texto, o título ou o período: quem já tinha dado "Ciente" vê de novo. */
    @Transactional
    public void alterar(Long id, RecadoReq req) {
        normalizar(req);
        Set<Long> unidades = unidadesValidas(req.getUnidades());
        List<Map<String, Object>> antes = jdbcTemplate.queryForList(
                "SELECT titulo, texto, inicio, fim FROM calendario.recado WHERE id = ? FOR UPDATE", id);
        if (antes.isEmpty()) {
            throw exception(NAO_ENCONTRADO);
        }
        jdbcTemplate.update("UPDATE calendario.recado SET titulo = ?, texto = ?, inicio = ?, fim = ?, cor = ?, update_time = now() WHERE id = ?",
                req.getTitulo(), req.getTexto(), req.getInicio(), req.getFim(), req.getCor(), id);
        jdbcTemplate.update("DELETE FROM calendario.recado_unidade WHERE recado_id = ?", id);
        unidades.forEach(d -> jdbcTemplate.update("INSERT INTO calendario.recado_unidade (recado_id, dept_id) VALUES (?, ?)", id, d));
        Map<String, Object> a = antes.get(0);
        if (mudouConteudo(a, req)) {
            jdbcTemplate.update("DELETE FROM calendario.recado_ciente WHERE recado_id = ?", id);
        }
    }

    static boolean mudouConteudo(Map<String, Object> antes, RecadoReq req) {
        return !Objects.equals(antes.get("titulo"), req.getTitulo()) || !Objects.equals(antes.get("texto"), req.getTexto())
                || !Objects.equals(data(antes.get("inicio")), req.getInicio()) || !Objects.equals(data(antes.get("fim")), req.getFim());
    }

    private static LocalDate data(Object o) {
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return (LocalDate) o;
    }

    public void excluir(Long id) {
        if (jdbcTemplate.update("DELETE FROM calendario.recado WHERE id = ?", id) == 0) {
            throw exception(NAO_ENCONTRADO);
        }
    }

    private static String arrayPg(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

}
