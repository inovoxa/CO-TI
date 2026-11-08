package cn.iocoder.yudao.module.infraestrutura.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.IntegracaoVOs;
import cn.iocoder.yudao.module.infraestrutura.dominio.EntradaRegras;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CatalogoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.FormularioDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.service.FormularioService;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Chaves de API. A chave aparece uma vez e só o hash fica guardado. Esta API não dispara operação no AD: formulário com ação no AD
 * fica de fora, porque aqui vale a validação e a aprovação normais, e o bloqueio automático tem as travas próprias das entradas.
 */
@Service
public class IntegracaoApiService {

    public static final String CRIAR = "chamado:criar";
    public static final String CONSULTAR = "chamado:consultar";
    private static final Set<String> ESCOPOS = Set.of(CRIAR, CONSULTAR);
    private static final ErrorCode INVALIDA = new ErrorCode(1_098_000_002, "{}");
    private static final int LIMITE_POR_MINUTO = 120;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record ChaveAtiva(Long id, Set<String> escopos, Long solicitanteId) {
    }

    public record Criado(Long id, boolean repetido) {
    }

    public enum Falha { NAO_AUTORIZADO, SEM_ESCOPO, LIMITE }

    public static class ApiException extends RuntimeException {
        public final Falha falha;

        public ApiException(Falha falha) {
            super(falha.name(), null, false, false);
            this.falha = falha;
        }
    }

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FormularioService formularioService;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private AuditoriaService auditoria;

    // ---------------------------------------------------------------- uso

    public ChaveAtiva autenticar(String autorizacao, String ip, String escopo) {
        if (autorizacao == null || !autorizacao.startsWith("Bearer coti_")) {
            throw new ApiException(Falha.NAO_AUTORIZADO);
        }
        String hash = EntradaRegras.hash(autorizacao.substring(7).getBytes(StandardCharsets.UTF_8));
        List<Map<String, Object>> r = jdbcTemplate.queryForList("SELECT id, escopos, solicitante_id, ips_permitidos FROM integracao.chave_api "
                + "WHERE hash = ? AND ativa AND (expira_em IS NULL OR expira_em > now())", hash);
        if (r.isEmpty() || !EntradaRegras.ipPermitido(ip, (String) r.get(0).get("ips_permitidos"))) {
            throw new ApiException(Falha.NAO_AUTORIZADO);
        }
        Long id = ((Number) r.get(0).get("id")).longValue();
        Set<String> escopos = new HashSet<>(Arrays.asList(((String) r.get(0).get("escopos")).split(",")));
        if (!escopos.contains(escopo)) {
            throw new ApiException(Falha.SEM_ESCOPO);
        }
        conferirLimite(id);
        jdbcTemplate.update("UPDATE integracao.chave_api SET ultimo_uso = now() WHERE id = ? AND (ultimo_uso IS NULL OR ultimo_uso < now() - interval '1 minute')", id);
        return new ChaveAtiva(id, escopos, ((Number) r.get(0).get("solicitante_id")).longValue());
    }

    private void conferirLimite(Long id) {
        String k = "coti:api:limite:" + id + ":" + (System.currentTimeMillis() / 60_000);
        Long n = stringRedisTemplate.opsForValue().increment(k);
        if (n != null && n == 1) {
            stringRedisTemplate.expire(k, Duration.ofMinutes(2));
        }
        if (n != null && n > LIMITE_POR_MINUTO) {
            throw new ApiException(Falha.LIMITE);
        }
    }

    /** O mesmo Idempotency-Key devolve o chamado já aberto. Sem ele, cada pedido abre um. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Criado abrir(ChaveAtiva c, IntegracaoVOs.AbrirChamado req, String idempotencia) {
        String chave = idempotencia == null || idempotencia.isBlank() ? null : idempotencia.strip();
        if (chave != null && chave.length() > 128) {
            throw exception(INVALIDA, "Idempotency-Key: no máximo 128 caracteres");
        }
        // trava por chave e pedido: dois pedidos iguais ao mesmo tempo não abrem dois chamados
        String guarda = chave != null ? chave : UUID.randomUUID().toString();
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))::text", String.class, c.id() + ":" + guarda);
        List<Long> existente = jdbcTemplate.queryForList("SELECT chamado_id FROM integracao.chave_chamado WHERE chave_id = ? AND idempotencia = ?",
                Long.class, c.id(), guarda);
        if (!existente.isEmpty()) {
            return new Criado(existente.get(0), true);
        }
        FormularioDO f = formularioService.obter(req.getFormularioId());
        if (f.getAcaoAd() != null) {
            throw exception(INVALIDA, "Este formulário faz uma operação no AD e não pode ser aberto pela API");
        }
        CatalogoVOs.EnviarReq e = new CatalogoVOs.EnviarReq();
        e.setFormularioId(req.getFormularioId());
        e.setRespostas(req.getRespostas() == null ? new HashMap<>() : req.getRespostas());
        Long id = formularioService.enviar(e, c.solicitanteId());
        jdbcTemplate.update("INSERT INTO integracao.chave_chamado (chave_id, chamado_id, idempotencia) VALUES (?, ?, ?)", c.id(), id, guarda);
        return new Criado(id, false);
    }

    /** Uma chave só enxerga os chamados que ela abriu. */
    @DataPermission(enable = false)
    public Optional<IntegracaoVOs.ChamadoApi> consultar(ChaveAtiva c, Long id) {
        Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM integracao.chave_chamado WHERE chave_id = ? AND chamado_id = ?", Integer.class, c.id(), id);
        ChamadoDO ch = n == null || n == 0 ? null : chamadoMapper.selectById(id);
        return ch == null ? Optional.empty() : Optional.of(new IntegracaoVOs.ChamadoApi(ch.getId(), ch.getTitulo(), ch.getStatus(), ch.getCategoriaId(),
                ch.getAbertoEm(), ch.getSolucionadoEm(), ch.getFechadoEm()));
    }

    // ---------------------------------------------------------------- cadastro

    public List<IntegracaoVOs.ChaveApi> listar() {
        return jdbcTemplate.query("SELECT * FROM integracao.chave_api ORDER BY nome", (rs, i) -> {
            Timestamp expira = rs.getTimestamp("expira_em");
            Timestamp uso = rs.getTimestamp("ultimo_uso");
            IntegracaoVOs.ChaveApi v = new IntegracaoVOs.ChaveApi();
            v.setId(rs.getLong("id"));
            v.setNome(rs.getString("nome"));
            v.setPrefixo(rs.getString("prefixo"));
            v.setEscopos(Arrays.asList(rs.getString("escopos").split(",")));
            v.setSolicitanteId(rs.getLong("solicitante_id"));
            v.setIpsPermitidos(rs.getString("ips_permitidos"));
            v.setAtiva(rs.getBoolean("ativa"));
            v.setExpiraEm(expira == null ? null : expira.toLocalDateTime());
            v.setUltimoUso(uso == null ? null : uso.toLocalDateTime());
            return v;
        });
    }

    /** @return a chave, que não pode ser vista de novo */
    public String criar(IntegracaoVOs.ChaveApi r, Long userId) {
        if (r.getEscopos() == null || r.getEscopos().isEmpty() || !ESCOPOS.containsAll(r.getEscopos())) {
            throw exception(INVALIDA, "Escolha o que a chave pode fazer");
        }
        AdminUserDO s = r.getSolicitanteId() == null ? null : adminUserMapper.selectById(r.getSolicitanteId());
        if (s == null || !CommonStatusEnum.isEnable(s.getStatus())) {
            throw exception(INVALIDA, "Escolha uma pessoa ativa como solicitante");
        }
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        String chave = "coti_" + HexFormat.of().formatHex(b);
        jdbcTemplate.update("INSERT INTO integracao.chave_api (id, nome, prefixo, hash, escopos, solicitante_id, ips_permitidos, expira_em) "
                        + "VALUES (nextval('integracao.chave_api_seq'), ?, ?, ?, ?, ?, ?, ?)", r.getNome().strip(), chave.substring(0, 12),
                EntradaRegras.hash(chave.getBytes(StandardCharsets.UTF_8)), String.join(",", r.getEscopos()), r.getSolicitanteId(),
                r.getIpsPermitidos() == null || r.getIpsPermitidos().isBlank() ? null : r.getIpsPermitidos().strip(),
                r.getExpiraEm() == null ? null : Timestamp.valueOf(r.getExpiraEm()));
        auditoria.registrar("integracao.chave_criada", userId, null, Map.of("nome", r.getNome(), "prefixo", chave.substring(0, 12)));
        return chave;
    }

    public void revogar(Long id, Long userId) {
        if (jdbcTemplate.update("UPDATE integracao.chave_api SET ativa = false WHERE id = ?", id) == 0) {
            throw exception(INVALIDA, "Chave não encontrada");
        }
        auditoria.registrar("integracao.chave_revogada", userId, null, Map.of("chave", id));
    }

}
