package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.identidade.dal.dataobject.MfaTotpDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.MfaTotpMapper;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.seguranca.Totp;
import cn.iocoder.yudao.module.system.service.auth.EventosSeguranca;
import cn.iocoder.yudao.module.system.service.auth.SegundoFator;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.identidade.enums.ErrorCodeConstants.*;

/**
 * Verificação em duas etapas por aplicativo autenticador (TOTP) com códigos de recuperação de uso único.
 */
@Slf4j
@Service
public class MfaService implements SegundoFator {

    static final int QTD_CODIGOS_RECUPERACAO = 10;
    /** Sem 0/O, 1/I/L: o usuário digita o código lendo de um papel */
    private static final String ALFABETO_RECUPERACAO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private MfaTotpMapper mfaTotpMapper;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private AuditoriaService auditoria;

    @org.springframework.beans.factory.annotation.Value("${coti.seguranca.mfa.emissor:CO-TI}")
    private String emissor;

    @Value
    public static class Status {
        boolean ativo;
        int codigosRecuperacaoRestantes;
    }

    @Value
    public static class Cadastro {
        String segredo;
        String uri;
    }

    // ========== SegundoFator (login) ==========

    @Override
    @DataPermission(enable = false)
    public boolean exige(Long userId) {
        MfaTotpDO m = mfaTotpMapper.selectByUserId(userId);
        return m != null && Boolean.TRUE.equals(m.getAtivo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public boolean verificar(Long userId, String codigo) {
        MfaTotpDO m = mfaTotpMapper.selectByUserIdParaAtualizar(userId);
        if (m == null || !Boolean.TRUE.equals(m.getAtivo()) || codigo == null) {
            return false;
        }
        if (codigo.length() == Totp.DIGITOS) {
            return aceitarTotp(m, codigo);
        }
        return consumirCodigoRecuperacao(m, codigo);
    }

    // ========== Cadastro pelo próprio usuário ==========

    @DataPermission(enable = false)
    public Status status(Long userId) {
        MfaTotpDO m = mfaTotpMapper.selectByUserId(userId);
        boolean ativo = m != null && Boolean.TRUE.equals(m.getAtivo());
        return new Status(ativo, ativo ? lerHashes(m).size() : 0);
    }

    /** Gera o segredo e devolve o que vai no QR code. Só vale depois de {@link #confirmar}. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Cadastro iniciar(Long userId, String login) {
        if (!cifrador.configurado()) {
            throw exception(MFA_CHAVE_MESTRA_AUSENTE);
        }
        MfaTotpDO m = mfaTotpMapper.selectByUserIdParaAtualizar(userId);
        if (m != null && Boolean.TRUE.equals(m.getAtivo())) {
            throw exception(MFA_JA_ATIVO);
        }
        String segredo = Totp.novoSegredo();
        if (m == null) {
            m = MfaTotpDO.builder().userId(userId).build();
        }
        m.setSegredoCifrado(cifrador.cifrar(segredo, contexto(userId)));
        m.setAtivo(false);
        m.setUltimoPasso(0L);
        m.setCodigosRecuperacao("[]");
        if (m.getId() == null) {
            mfaTotpMapper.insert(m);
        } else {
            mfaTotpMapper.updateById(m);
        }
        return new Cadastro(segredo, Totp.uri(emissor, login, segredo));
    }

    /** Ativa com o primeiro código do aplicativo e devolve os códigos de recuperação (só aparecem agora). */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public List<String> confirmar(Long userId, String login, String codigo) {
        MfaTotpDO m = mfaTotpMapper.selectByUserIdParaAtualizar(userId);
        if (m == null) {
            throw exception(MFA_NAO_INICIADO);
        }
        if (Boolean.TRUE.equals(m.getAtivo())) {
            throw exception(MFA_JA_ATIVO);
        }
        OptionalLong passo = Totp.verificar(cifrador.decifrar(m.getSegredoCifrado(), contexto(userId)), codigo,
                Instant.now().getEpochSecond());
        if (passo.isEmpty()) {
            throw exception(MFA_CODIGO_INVALIDO);
        }
        List<String> codigos = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < QTD_CODIGOS_RECUPERACAO; i++) {
            String c = novoCodigoRecuperacao();
            codigos.add(c);
            hashes.add(passwordEncoder.encode(normalizar(c)));
        }
        m.setAtivo(true);
        m.setUltimoPasso(passo.getAsLong());
        m.setAtivadoEm(LocalDateTime.now());
        m.setCodigosRecuperacao(JsonUtils.toJsonString(hashes));
        mfaTotpMapper.updateById(m);
        auditoria.registrar("mfa.ativado", userId, login, Map.of());
        return codigos;
    }

    /** Desativar exige um código válido: um token roubado sozinho não desliga a proteção. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void desativar(Long userId, String login, String codigo) {
        if (!exige(userId)) {
            throw exception(MFA_NAO_ATIVO);
        }
        if (!verificar(userId, codigo)) {
            throw exception(MFA_CODIGO_INVALIDO);
        }
        mfaTotpMapper.delete(MfaTotpDO::getUserId, userId);
        auditoria.registrar("mfa.desativado", userId, login, Map.of());
    }

    /** Para quem perdeu o celular e os códigos: um administrador remove e a pessoa cadastra de novo. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void redefinir(Long alvoUserId, Long adminUserId, String adminLogin) {
        mfaTotpMapper.delete(MfaTotpDO::getUserId, alvoUserId);
        auditoria.registrar("mfa.redefinido_por_admin", adminUserId, adminLogin, Map.of("usuarioAlvo", alvoUserId));
    }

    // ========== internos ==========

    private boolean aceitarTotp(MfaTotpDO m, String codigo) {
        OptionalLong passo = Totp.verificar(cifrador.decifrar(m.getSegredoCifrado(), contexto(m.getUserId())), codigo,
                Instant.now().getEpochSecond());
        // mesmo código (ou um mais antigo) já usado: recusa, senão quem viu o código na tela poderia reusar
        if (passo.isEmpty() || passo.getAsLong() <= m.getUltimoPasso()) {
            return false;
        }
        m.setUltimoPasso(passo.getAsLong());
        mfaTotpMapper.updateById(m);
        return true;
    }

    private boolean consumirCodigoRecuperacao(MfaTotpDO m, String codigo) {
        String informado = normalizar(codigo);
        List<String> hashes = lerHashes(m);
        for (Iterator<String> it = hashes.iterator(); it.hasNext(); ) {
            if (passwordEncoder.matches(informado, it.next())) {
                it.remove();
                m.setCodigosRecuperacao(JsonUtils.toJsonString(hashes));
                mfaTotpMapper.updateById(m);
                auditoria.registrar("mfa.codigo_recuperacao_usado", m.getUserId(), null,
                        Map.of("restantes", hashes.size()));
                return true;
            }
        }
        return false;
    }

    private static List<String> lerHashes(MfaTotpDO m) {
        List<String> l = JsonUtils.parseObject(m.getCodigosRecuperacao(), new TypeReference<List<String>>() {
        });
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    static String novoCodigoRecuperacao() {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(ALFABETO_RECUPERACAO.charAt(RANDOM.nextInt(ALFABETO_RECUPERACAO.length())));
        }
        return sb.toString();
    }

    static String normalizar(String codigo) {
        return codigo.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String contexto(Long userId) {
        return "mfa:" + userId;
    }

}
