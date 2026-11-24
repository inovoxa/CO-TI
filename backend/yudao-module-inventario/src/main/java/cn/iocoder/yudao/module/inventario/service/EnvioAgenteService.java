package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.EnvioVOs;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.RELATORIO_GRANDE_DEMAIS;

/**
 * Registro de cada pedido dos agentes (aceito ou recusado), para saber quem está chegando, quem foi recusado e por quê.
 * <p>
 * Resultados: {@code recusado_rede}, {@code recusado_senha}, {@code invalido} (pedido que não dá para ler),
 * {@code enviar} / {@code aguardar} (resposta ao PROLOG), {@code gravado} / {@code erro} (inventário), {@code pacote}
 * (resultado de implantação) e {@code nao_suportado}.
 * <p>
 * Recusas repetidas do mesmo IP entram uma vez por minuto (um agente mal configurado não enche a tabela). Falhar ao
 * registrar nunca atrapalha o agente.
 */
@Service
@Slf4j
public class EnvioAgenteService {

    static final Set<String> RECUSAS = Set.of("recusado_rede", "recusado_senha", "invalido");
    static final int LIMITE_EXPORTACAO = 50_000;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private AuditoriaService auditoriaService;

    private final Map<String, Long> ultimasRecusas = new ConcurrentHashMap<>();

    public record Envio(String ip, String deviceId, String formato, String consulta, String resultado, String detalhe,
                        String agente, Integer bytes, Integer ms) {
    }

    public void registrar(Envio e) {
        try {
            if (RECUSAS.contains(e.resultado()) && !deveRegistrarRecusa(e.ip() + "|" + e.resultado(), System.currentTimeMillis())) {
                return;
            }
            jdbcTemplate.update("""
                            INSERT INTO cmdb.agente_envio (ip, device_id, formato, consulta, resultado, detalhe, agente, bytes, ms, ativo_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT id FROM cmdb.ativo WHERE device_id = ? AND deleted = 0 LIMIT 1))
                            """, corte(e.ip(), 64), corte(e.deviceId(), 255), e.formato(), corte(e.consulta(), 20), e.resultado(),
                    corte(e.detalhe(), 500), corte(e.agente(), 255), e.bytes(), e.ms(), e.deviceId());
        } catch (RuntimeException ex) {
            log.warn("[inventario] não foi possível registrar o envio de {}: {}", e.ip(), ex.getMessage());
        }
    }

    /** Uma recusa por IP e motivo a cada minuto. */
    boolean deveRegistrarRecusa(String chave, long agora) {
        if (ultimasRecusas.size() > 10_000) {
            ultimasRecusas.clear();
        }
        boolean[] registrar = {false};
        ultimasRecusas.compute(chave, (k, antes) -> {
            if (antes == null || agora - antes >= 60_000) {
                registrar[0] = true;
                return agora;
            }
            return antes;
        });
        return registrar[0];
    }

    /** Apaga o que passou do prazo ({@code coti.inventario.envios-dias}). */
    public int apagarAntigos() {
        int dias = Math.max(inventarioProperties.getEnviosDias(), 1);
        return jdbcTemplate.update("DELETE FROM cmdb.agente_envio WHERE quando < ?", Timestamp.valueOf(LocalDateTime.now().minusDays(dias)));
    }

    // ======================================================================================= consulta

    public PageResult<EnvioVOs.Resp> pagina(EnvioVOs.PaginaReq req) {
        List<Object> args = new ArrayList<>();
        String where = filtro(req, args);
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM cmdb.agente_envio e LEFT JOIN cmdb.ativo a ON a.id = e.ativo_id"
                + where, Long.class, args.toArray());
        int tamanho = Math.min(Math.max(req.getPageSize(), 1), 200);
        args.add(tamanho);
        args.add((Math.max(req.getPageNo(), 1) - 1) * tamanho);
        List<EnvioVOs.Resp> l = jdbcTemplate.query(SELECT + where + " ORDER BY e.id DESC LIMIT ? OFFSET ?", (rs, i) -> linha(rs), args.toArray());
        return new PageResult<>(l, total == null ? 0 : total);
    }

    private static final String SELECT = "SELECT e.*, a.nome AS ativo_nome, a.deleted AS ativo_removido FROM cmdb.agente_envio e"
            + " LEFT JOIN cmdb.ativo a ON a.id = e.ativo_id";

    private static String filtro(EnvioVOs.PaginaReq req, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        String r = req.getResultado();
        if ("recusados".equals(r)) {
            w.append(" AND e.resultado IN ('recusado_rede', 'recusado_senha', 'invalido')");
        } else if ("problemas".equals(r)) {
            w.append(" AND e.resultado IN ('recusado_rede', 'recusado_senha', 'invalido', 'erro')");
        } else if ("inventarios".equals(r)) {
            w.append(" AND e.resultado IN ('gravado', 'erro')");
        } else if (r != null && !r.isBlank()) {
            w.append(" AND e.resultado = ?");
            args.add(r.trim());
        }
        if (req.getAtivoId() != null) {
            w.append(" AND e.ativo_id = ?");
            args.add(req.getAtivoId());
        }
        if (req.getHoras() != null && req.getHoras() > 0) {
            w.append(" AND e.quando >= ?");
            args.add(Timestamp.valueOf(LocalDateTime.now().minusHours(req.getHoras())));
        }
        if (req.getBusca() != null && !req.getBusca().isBlank()) {
            String b = "%" + req.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (e.ip ILIKE ? OR e.device_id ILIKE ? OR a.nome ILIKE ? OR e.agente ILIKE ?)");
            Collections.addAll(args, b, b, b, b);
        }
        return w.toString();
    }

    private static EnvioVOs.Resp linha(ResultSet rs) throws SQLException {
        EnvioVOs.Resp vo = new EnvioVOs.Resp();
        vo.setId(rs.getLong("id"));
        vo.setQuando(rs.getTimestamp("quando").toLocalDateTime());
        vo.setIp(rs.getString("ip"));
        vo.setDeviceId(rs.getString("device_id"));
        vo.setFormato(rs.getString("formato"));
        vo.setConsulta(rs.getString("consulta"));
        vo.setResultado(rs.getString("resultado"));
        vo.setDetalhe(rs.getString("detalhe"));
        vo.setAgente(rs.getString("agente"));
        vo.setBytes((Integer) rs.getObject("bytes"));
        vo.setMs((Integer) rs.getObject("ms"));
        long ativo = rs.getLong("ativo_id");
        vo.setAtivoId(rs.wasNull() ? null : ativo);
        vo.setAtivoNome(rs.getString("ativo_nome"));
        vo.setAtivoRemovido(rs.getInt("ativo_removido") != 0);
        return vo;
    }

    /** Totais das últimas 24 h e os IPs recusados (aba "Envios"). */
    public EnvioVOs.Resumo resumo() {
        Timestamp desde = Timestamp.valueOf(LocalDateTime.now().minusHours(24));
        EnvioVOs.Resumo r = new EnvioVOs.Resumo();
        jdbcTemplate.query("SELECT resultado, count(*) AS n FROM cmdb.agente_envio WHERE quando >= ? GROUP BY resultado",
                (RowCallbackHandler) rs -> r.getPorResultado().put(rs.getString("resultado"), rs.getLong("n")), desde);
        r.setMaquinas(Objects.requireNonNullElse(jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT device_id) FROM cmdb.agente_envio WHERE quando >= ? AND resultado NOT IN ('recusado_rede', 'recusado_senha', 'invalido')",
                Long.class, desde), 0L));
        r.setRecusas(jdbcTemplate.query("""
                SELECT ip, resultado, count(*) AS n, max(quando) AS ultima FROM cmdb.agente_envio
                WHERE quando >= ? AND resultado IN ('recusado_rede', 'recusado_senha', 'invalido')
                GROUP BY ip, resultado ORDER BY max(quando) DESC LIMIT 20
                """, (rs, i) -> {
            EnvioVOs.Recusa x = new EnvioVOs.Recusa();
            x.setIp(rs.getString("ip"));
            x.setResultado(rs.getString("resultado"));
            x.setVezes(rs.getLong("n"));
            x.setUltima(rs.getTimestamp("ultima").toLocalDateTime());
            return x;
        }, desde));
        r.setDias(inventarioProperties.getEnviosDias());
        return r;
    }

    public List<Planilha.Aba> exportar(EnvioVOs.PaginaReq req, Long userId) {
        List<Object> args = new ArrayList<>();
        String where = filtro(req, args);
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM cmdb.agente_envio e LEFT JOIN cmdb.ativo a ON a.id = e.ativo_id"
                + where, Long.class, args.toArray());
        if (total != null && total > LIMITE_EXPORTACAO) {
            throw exception(RELATORIO_GRANDE_DEMAIS, total, LIMITE_EXPORTACAO);
        }
        List<List<Object>> linhas = new ArrayList<>();
        jdbcTemplate.query(SELECT + where + " ORDER BY e.id DESC", (RowCallbackHandler) rs -> {
            EnvioVOs.Resp e = linha(rs);
            linhas.add(Arrays.asList(Planilha.dataHora(e.getQuando()), e.getAtivoNome(), e.getIp(), e.getDeviceId(), e.getFormato(),
                    e.getConsulta(), e.getResultado(), e.getDetalhe(), e.getAgente(), e.getBytes(), e.getMs()));
        }, args.toArray());
        auditoriaService.registrar("relatorio.exportado", userId, null, Map.of("relatorio", "inventario_envios", "linhas", linhas.size()));
        return List.of(new Planilha.Aba("Envios", List.of("Quando", "Máquina", "IP", "DEVICEID", "Agente (tipo)", "Consulta",
                "Resultado", "Detalhe", "Versão do agente", "Bytes", "Tempo (ms)"), linhas));
    }

    private static String corte(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

}
