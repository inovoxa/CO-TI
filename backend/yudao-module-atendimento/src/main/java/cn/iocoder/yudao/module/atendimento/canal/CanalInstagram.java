package cn.iocoder.yudao.module.atendimento.canal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Direct do Instagram (API do Instagram com login do Instagram). Texto livre até 24 h depois da última mensagem do
 * contato. O Instagram não recebe o arquivo: busca pelo endereço, então os anexos vão como link público do CO-TI
 * (caminho aleatório de 128 bits) e exigem {@code CO_TI_URL_PUBLICA}.
 */
@Component
public class CanalInstagram implements Canal {

    @Resource
    private ProvedorHttp http;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private AtendimentoProperties props;

    @Override
    public String tipo() {
        return "instagram";
    }

    @Override
    public String enviar(CaixaDO caixa, ConversaDO conversa, ContatoDO contato, ContatoCanalDO endereco, MensagemDO m) throws Exception {
        CanalSegredos.Config cfg = exigir(caixa);
        List<AtendimentoVOs.Anexo> anexos = JsonUtils.parseObject(m.getAnexos(), new TypeReference<List<AtendimentoVOs.Anexo>>() {
        });
        String id = null;
        for (AtendimentoVOs.Anexo a : anexos == null ? List.<AtendimentoVOs.Anexo>of() : anexos) {
            String base = props.getUrlPublica() == null ? "" : props.getUrlPublica().replaceAll("/+$", "");
            if (base.isEmpty()) {
                throw new FalhaDefinitiva("para mandar anexos pelo Instagram, configure o endereço público do CO-TI (CO_TI_URL_PUBLICA)");
            }
            String tipo = a.getTipo() == null ? "" : a.getTipo();
            String t = tipo.startsWith("image/") ? "image" : tipo.startsWith("video/") ? "video" : tipo.startsWith("audio/") ? "audio" : "file";
            id = mandar(cfg, endereco.getOrigemId(), Map.of("attachment", Map.of("type", t, "payload", Map.of("url", base + a.getUrl()))));
        }
        String texto = m.getConteudo() == null ? "" : m.getConteudo().strip();
        if (!texto.isEmpty()) {
            id = mandar(cfg, endereco.getOrigemId(), Map.of("text", texto));
        }
        return id;
    }

    @Override
    public void avisarLeitura(CaixaDO caixa, ContatoCanalDO endereco, MensagemDO entrada) throws Exception {
        CanalSegredos.Config cfg = exigir(caixa);
        Map<String, Object> corpo = Map.of("recipient", Map.of("id", endereco.getOrigemId()), "sender_action", "mark_seen");
        ProvedorHttp.Resposta r = http.postJson(props.getInstagramUrl() + "/" + cfg.igUserId() + "/messages", cfg.token(), corpo);
        if (!r.ok()) {
            throw CanalWhatsappCloud.erro(r);
        }
    }

    @Override
    public Map<String, Object> conectar(CaixaDO caixa) throws Exception {
        CanalSegredos.Config cfg = segredos.ler(caixa);
        if (cfg.token() == null) {
            throw new FalhaDefinitiva("a caixa não tem o token do Instagram configurado");
        }
        ProvedorHttp.Resposta eu = http.get(props.getInstagramUrl() + "/me?fields=user_id,username,name,account_type", cfg.token());
        if (!eu.ok()) {
            throw CanalWhatsappCloud.erro(eu);
        }
        String userId = eu.json().get("user_id") == null ? null : eu.json().get("user_id").toString();
        if (userId == null) {
            throw new FalhaDefinitiva("o Instagram não informou a conta do token");
        }
        if (cfg.igUserId() != null && !cfg.igUserId().equals(userId)) {
            throw new FalhaDefinitiva("o token é da conta " + userId + ", não da conta " + cfg.igUserId() + " informada");
        }
        ProvedorHttp.Resposta assinatura = http.postJson(props.getInstagramUrl() + "/" + userId + "/subscribed_apps?subscribed_fields=messages,messaging_seen",
                cfg.token(), Map.of());
        if (!assinatura.ok()) {
            throw CanalWhatsappCloud.erro(assinatura);
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("user_id", userId);
        for (String campo : List.of("username", "name", "account_type")) {
            if (eu.json().get(campo) != null) {
                info.put(campo, eu.json().get(campo));
            }
        }
        info.put("assinado", true);
        return info;
    }

    /** Nome e usuário de quem escreveu (só para contato novo; sem permissão, fica sem nome). */
    public Map<String, Object> perfil(CanalSegredos.Config cfg, String igsid) {
        try {
            ProvedorHttp.Resposta r = http.get(props.getInstagramUrl() + "/" + igsid + "?fields=name,username", cfg.token());
            return r.ok() ? r.json() : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    CanalSegredos.Config exigir(CaixaDO caixa) {
        CanalSegredos.Config cfg = segredos.ler(caixa);
        if (cfg.igUserId() == null || cfg.token() == null) {
            throw new FalhaDefinitiva("a caixa não tem a conta e o token do Instagram configurados");
        }
        return cfg;
    }

    private String mandar(CanalSegredos.Config cfg, String para, Map<String, Object> mensagem) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("recipient", Map.of("id", para));
        corpo.put("message", mensagem);
        ProvedorHttp.Resposta r = http.postJson(props.getInstagramUrl() + "/" + cfg.igUserId() + "/messages", cfg.token(), corpo);
        if (!r.ok()) {
            throw CanalWhatsappCloud.erro(r);
        }
        Object id = r.json().get("message_id");
        return id == null ? null : id.toString();
    }

}
