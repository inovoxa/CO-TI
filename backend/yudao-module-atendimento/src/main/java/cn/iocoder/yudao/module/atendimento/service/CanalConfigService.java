package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.canal.Canal;
import cn.iocoder.yudao.module.atendimento.canal.CanalSegredos;
import cn.iocoder.yudao.module.atendimento.canal.CanalTelegram;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.CaixaMapper;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.atendimento.service.ContatoService.vazio;

/**
 * Configuração dos canais WhatsApp oficial, Instagram e Telegram: credenciais (cifradas), teste de conexão e o
 * endereço do webhook. Os segredos nunca voltam para a tela; só "definido".
 */
@Slf4j
@Service
public class CanalConfigService {

    public static final Set<String> CANAIS = Set.of("whatsapp_cloud", "instagram", "telegram");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Resource
    private CaixaService caixaService;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private List<Canal> canais;
    @Resource
    private CanalTelegram canalTelegram;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    private AtendimentoProperties props;

    public AtendimentoVOs.CanalConfigResp obter(Long caixaId) {
        CaixaDO c = caixaService.existente(caixaId);
        exigirCanal(c);
        return resposta(c);
    }

    public AtendimentoVOs.CanalConfigResp salvar(AtendimentoVOs.CanalConfig req, Long userId) {
        CaixaDO c = caixaService.existente(req.getCaixaId());
        exigirCanal(c);
        if (!segredos.configurado()) {
            throw exception(CAIXA_SEM_CHAVE_MESTRA);
        }
        Map<String, Object> m = segredos.bruto(c);
        Set<String> alterados = new TreeSet<>();
        switch (c.getCanal()) {
            case "whatsapp_cloud" -> {
                texto(m, "phoneNumberId", req.getPhoneNumberId(), alterados);
                texto(m, "wabaId", req.getWabaId(), alterados);
                m.put("coexistencia", Boolean.TRUE.equals(req.getCoexistencia()));
                segredo(m, "token", req.getToken(), alterados);
                segredo(m, "appSecret", req.getAppSecret(), alterados);
                obrigatorio(m, "phoneNumberId", "o identificador do número (Phone number ID)");
                obrigatorio(m, "wabaId", "o identificador da conta do WhatsApp Business (WABA ID)");
                obrigatorio(m, "token", "o token de acesso");
                obrigatorio(m, "appSecret", "a chave secreta do app (App Secret), que confere a assinatura dos webhooks");
                unico(c, "phoneNumberId", m.get("phoneNumberId"), "Esse número");
                m.putIfAbsent("verifyToken", aleatorio(18));
            }
            case "instagram" -> {
                texto(m, "igUserId", req.getIgUserId(), alterados);
                segredo(m, "token", req.getToken(), alterados);
                segredo(m, "appSecret", req.getAppSecret(), alterados);
                obrigatorio(m, "token", "o token de acesso do Instagram");
                obrigatorio(m, "appSecret", "a chave secreta do app (App Secret), que confere a assinatura dos webhooks");
                if (m.get("igUserId") != null) {
                    unico(c, "igUserId", m.get("igUserId"), "Essa conta do Instagram");
                }
                m.putIfAbsent("verifyToken", aleatorio(18));
            }
            default -> {
                segredo(m, "botToken", req.getBotToken(), alterados);
                obrigatorio(m, "botToken", "o token do robô (fornecido pelo @BotFather)");
            }
        }
        if (!alterados.isEmpty()) {
            // credencial nova: o teste anterior não vale mais
            m.remove("testadoEm");
            m.remove("erroTeste");
        }
        gravar(c.getId(), m);
        auditoria.registrar("atendimento.canal_configurado", userId, null, Map.of("caixa", c.getId(), "canal", c.getCanal(),
                "campos", String.join(",", alterados)));
        return resposta(caixaService.existente(c.getId()));
    }

    /** Testa as credenciais no provedor e liga o recebimento (assinatura do app ou cadastro do webhook). */
    public AtendimentoVOs.CanalConfigResp testar(Long caixaId, Long userId) {
        CaixaDO c = caixaService.existente(caixaId);
        exigirCanal(c);
        Map<String, Object> m = segredos.bruto(c);
        String agora = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try {
            Map<String, Object> info;
            if ("telegram".equals(c.getCanal())) {
                String base = urlPublica();
                if (base == null) {
                    throw exception(CANAL_SEM_URL_PUBLICA);
                }
                // segredo novo a cada cadastro: o Telegram o devolve em todo webhook
                String segredo = aleatorio(32);
                m.put("secretToken", segredos.cifrar(segredo));
                gravar(c.getId(), m);
                info = canalTelegram.conectar(c, base + "/admin-api/atendimento/canal/telegram/" + c.getId() + "/webhook", segredo);
            } else {
                Canal canal = canais.stream().filter(x -> x.tipo().equals(c.getCanal())).findFirst().orElseThrow();
                info = canal.conectar(c);
                if ("instagram".equals(c.getCanal()) && m.get("igUserId") == null && info.get("user_id") != null) {
                    unico(c, "igUserId", info.get("user_id").toString(), "Essa conta do Instagram");
                    m.put("igUserId", info.get("user_id").toString());
                    gravar(c.getId(), m);
                }
            }
            m = segredos.bruto(caixaService.existente(c.getId()));
            m.put("info", info);
            m.put("testadoEm", agora);
            m.remove("erroTeste");
            gravar(c.getId(), m);
            auditoria.registrar("atendimento.canal_testado", userId, null, Map.of("caixa", c.getId(), "resultado", "ok"));
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[testar][caixa {}: {}]", c.getId(), e.getMessage());
            m = segredos.bruto(caixaService.existente(c.getId()));
            m.put("testadoEm", agora);
            m.put("erroTeste", ContatoService.cortar(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 500));
            gravar(c.getId(), m);
            auditoria.registrar("atendimento.canal_testado", userId, null, Map.of("caixa", c.getId(), "resultado", "erro"));
            throw exception(CANAL_TESTE_FALHOU, "Teste falhou: " + m.get("erroTeste"));
        }
        return resposta(caixaService.existente(c.getId()));
    }

    /** Caixas ativas do canal (para os webhooks acharem a caixa pelo número ou pela conta). */
    public List<CaixaDO> ativas(String canal) {
        return caixaMapper.selectList(new LambdaQueryWrapperX<CaixaDO>().eq(CaixaDO::getCanal, canal).eq(CaixaDO::getAtivo, true));
    }

    private AtendimentoVOs.CanalConfigResp resposta(CaixaDO c) {
        Map<String, Object> m = segredos.bruto(c);
        AtendimentoVOs.CanalConfigResp r = new AtendimentoVOs.CanalConfigResp();
        r.setCaixaId(c.getId());
        r.setCanal(c.getCanal());
        r.setPhoneNumberId((String) m.get("phoneNumberId"));
        r.setWabaId((String) m.get("wabaId"));
        r.setCoexistencia(Boolean.TRUE.equals(m.get("coexistencia")));
        r.setIgUserId((String) m.get("igUserId"));
        r.setTokenDefinido(m.get("token") != null);
        r.setAppSecretDefinido(m.get("appSecret") != null);
        r.setBotTokenDefinido(m.get("botToken") != null);
        r.setVerifyToken((String) m.get("verifyToken"));
        String base = urlPublica();
        if (base != null) {
            r.setEnderecoWebhook("telegram".equals(c.getCanal()) ? base + "/admin-api/atendimento/canal/telegram/" + c.getId() + "/webhook"
                    : base + "/admin-api/atendimento/canal/meta/webhook");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> info = m.get("info") instanceof Map<?, ?> i ? (Map<String, Object>) i : null;
        r.setInfo(info);
        if (m.get("testadoEm") != null) {
            r.setTestadoEm(LocalDateTime.parse(m.get("testadoEm").toString()));
        }
        r.setErroTeste((String) m.get("erroTeste"));
        return r;
    }

    private void exigirCanal(CaixaDO c) {
        if (!CANAIS.contains(c.getCanal())) {
            throw exception(CANAL_TIPO_ERRADO, "WhatsApp oficial, Instagram ou Telegram");
        }
    }

    private void unico(CaixaDO c, String campo, Object valor, String rotulo) {
        for (CaixaDO outra : caixaMapper.selectList(new LambdaQueryWrapperX<CaixaDO>().eq(CaixaDO::getCanal, c.getCanal()).ne(CaixaDO::getId, c.getId()))) {
            if (valor != null && valor.equals(segredos.bruto(outra).get(campo))) {
                throw exception(CANAL_DUPLICADO, rotulo, outra.getNome());
            }
        }
    }

    private void gravar(Long caixaId, Map<String, Object> m) {
        caixaMapper.updateById(CaixaDO.builder().id(caixaId).config(JsonUtils.toJsonString(m)).build());
    }

    private static void texto(Map<String, Object> m, String campo, String valor, Set<String> alterados) {
        String v = vazio(valor);
        if (!Objects.equals(v, m.get(campo))) {
            alterados.add(campo);
        }
        if (v == null) {
            m.remove(campo);
        } else {
            m.put(campo, v);
        }
    }

    /** Segredo em branco mantém o atual. */
    private void segredo(Map<String, Object> m, String campo, String valor, Set<String> alterados) {
        String v = vazio(valor);
        if (v != null) {
            m.put(campo, segredos.cifrar(v));
            alterados.add(campo);
        }
    }

    private static void obrigatorio(Map<String, Object> m, String campo, String rotulo) {
        if (m.get(campo) == null) {
            throw exception(CANAL_CAMPO_OBRIGATORIO, rotulo);
        }
    }

    private String urlPublica() {
        String b = props.getUrlPublica() == null ? "" : props.getUrlPublica().trim().replaceAll("/+$", "");
        return b.isEmpty() ? null : b;
    }

    private static String aleatorio(int bytes) {
        byte[] b = new byte[bytes];
        ALEATORIO.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

}
