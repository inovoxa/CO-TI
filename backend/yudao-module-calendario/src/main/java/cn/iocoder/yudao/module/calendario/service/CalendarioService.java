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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Calendário: aniversariantes e a agenda individual.
 *
 * <ul>
 *   <li>A data de nascimento é a própria pessoa quem informa, em Meu perfil. Para os outros só aparece se ela marcar como
 *       pública, e só dia e mês: o ano, que revela a idade, nunca sai daqui para outra pessoa.</li>
 *   <li>Os compromissos são individuais: cada um vê e mexe só nos seus.</li>
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

    // ---------- agenda individual ----------

    public record Evento(Long id, String titulo, String descricao, String local, LocalDateTime inicio, LocalDateTime fim,
                         boolean diaInteiro, String cor) {
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
    }

    public List<Evento> eventos(Long userId, LocalDate de, LocalDate ate) {
        if (de == null || ate == null || ate.isBefore(de)) {
            throw exception(INVALIDO, "Período inválido");
        }
        if (de.plusDays(400).isBefore(ate)) {
            throw exception(INVALIDO, "O período pode ter até 400 dias");
        }
        return jdbcTemplate.query("""
                SELECT id, titulo, descricao, local, inicio, fim, dia_inteiro, cor FROM calendario.evento
                WHERE dono_id = ? AND inicio < ? AND COALESCE(fim, inicio) >= ?
                ORDER BY inicio, id LIMIT ?
                """, (rs, i) -> new Evento(rs.getLong("id"), rs.getString("titulo"), rs.getString("descricao"),
                        rs.getString("local"), rs.getObject("inicio", LocalDateTime.class),
                        rs.getObject("fim", LocalDateTime.class), rs.getBoolean("dia_inteiro"), rs.getString("cor")),
                userId, Timestamp.valueOf(ate.plusDays(1).atStartOfDay()), Timestamp.valueOf(de.atStartOfDay()),
                LIMITE_EVENTOS);
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
        return req;
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long criar(Long userId, EventoReq req) {
        normalizar(req);
        Long id = jdbcTemplate.queryForObject("SELECT nextval('calendario.evento_seq')", Long.class);
        jdbcTemplate.update("""
                INSERT INTO calendario.evento (id, dono_id, titulo, descricao, local, inicio, fim, dia_inteiro, cor)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, userId, req.getTitulo(), req.getDescricao(), req.getLocal(), Timestamp.valueOf(req.getInicio()),
                req.getFim() == null ? null : Timestamp.valueOf(req.getFim()), req.isDiaInteiro(), req.getCor());
        return id;
    }

    @Transactional
    public void alterar(Long userId, Long id, EventoReq req) {
        normalizar(req);
        int n = jdbcTemplate.update("""
                UPDATE calendario.evento SET titulo = ?, descricao = ?, local = ?, inicio = ?, fim = ?, dia_inteiro = ?, cor = ?,
                    update_time = now()
                WHERE id = ? AND dono_id = ?
                """, req.getTitulo(), req.getDescricao(), req.getLocal(), Timestamp.valueOf(req.getInicio()),
                req.getFim() == null ? null : Timestamp.valueOf(req.getFim()), req.isDiaInteiro(), req.getCor(), id, userId);
        if (n == 0) {
            throw exception(NAO_ENCONTRADO);
        }
    }

    public void excluir(Long userId, Long id) {
        if (jdbcTemplate.update("DELETE FROM calendario.evento WHERE id = ? AND dono_id = ?", id, userId) == 0) {
            throw exception(NAO_ENCONTRADO);
        }
    }

}
