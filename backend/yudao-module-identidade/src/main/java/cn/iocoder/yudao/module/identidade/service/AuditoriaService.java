package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.identidade.controller.admin.vo.AuditoriaPageReqVO;
import cn.iocoder.yudao.module.identidade.dal.dataobject.AuditoriaDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.AuditoriaMapper;
import cn.iocoder.yudao.module.system.service.auth.EventosSeguranca;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Trilha de auditoria somente-inserção com hash encadeado (SHA-256 da linha anterior + conteúdo desta).
 *
 * <p>O banco recusa UPDATE/DELETE/TRUNCATE (trigger). Quem tem acesso de superusuário ao PostgreSQL ainda pode
 * desligar o trigger; nesse caso {@link #verificar()} aponta a primeira linha adulterada ou faltante.
 */
@Slf4j
@Service
public class AuditoriaService implements EventosSeguranca {

    static final String GENESE = "0".repeat(64);
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final int LOTE = 1000;

    @Resource
    private AuditoriaMapper auditoriaMapper;

    @Value
    public static class Verificacao {
        boolean integra;
        long registrosConferidos;
        /** Primeira linha cuja cadeia não fecha (null se íntegra) */
        Long primeiroIdQuebrado;
        String motivo;
    }

    /**
     * Grava em transação própria: o registro fica mesmo se a operação que o gerou falhar depois
     * (ex.: login recusado que lança exceção).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void registrar(String acao, Long userId, String usuario, Map<String, Object> detalhes) {
        auditoriaMapper.travarCadeia();
        AuditoriaDO ultimo = auditoriaMapper.selectUltimo();
        AuditoriaDO r = AuditoriaDO.builder()
                // o id entra no hash, então vem da sequência antes do insert (a linha não pode ser corrigida depois)
                .id(auditoriaMapper.proximoId())
                .momento(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS))
                .acao(acao)
                .userId(userId)
                .usuario(usuario)
                .ip(ipAtual())
                .detalhes(JsonUtils.toJsonString(detalhes == null ? Map.of() : detalhes))
                .hashAnterior(ultimo == null ? GENESE : ultimo.getHash())
                .build();
        r.setHash(calcularHash(r));
        auditoriaMapper.insert(r);
    }

    public PageResult<AuditoriaDO> listar(AuditoriaPageReqVO req) {
        return auditoriaMapper.selectPage(req);
    }

    /** Recalcula a cadeia inteira, em lotes. */
    public Verificacao verificar() {
        String anterior = GENESE;
        long conferidos = 0;
        long aposId = 0;
        while (true) {
            List<AuditoriaDO> lote = auditoriaMapper.selectLote(aposId, LOTE);
            if (lote.isEmpty()) {
                return new Verificacao(true, conferidos, null, null);
            }
            for (AuditoriaDO r : lote) {
                if (!Objects.equals(anterior, r.getHashAnterior())) {
                    return new Verificacao(false, conferidos, r.getId(),
                            "hash_anterior não confere com a linha anterior (linha removida ou alterada antes desta)");
                }
                if (!Objects.equals(calcularHash(r), r.getHash())) {
                    return new Verificacao(false, conferidos, r.getId(), "conteúdo da linha foi alterado");
                }
                anterior = r.getHash();
                conferidos++;
                aposId = r.getId();
            }
        }
    }

    static String calcularHash(AuditoriaDO r) {
        String conteudo = String.join("\n",
                r.getHashAnterior(),
                String.valueOf(r.getId()),
                FORMATO.format(r.getMomento()),
                r.getAcao(),
                r.getUserId() == null ? "" : String.valueOf(r.getUserId()),
                r.getUsuario() == null ? "" : r.getUsuario(),
                r.getIp() == null ? "" : r.getIp(),
                r.getDetalhes());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(conteudo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String ipAtual() {
        try {
            return ServletUtils.getClientIP();
        } catch (Exception e) {
            return null; // fora de uma requisição (tarefa agendada)
        }
    }

}
