package cn.iocoder.yudao.module.atendimento.canal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dominio.Meta;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * WhatsApp oficial (WhatsApp Business Platform, Cloud API). Texto livre só até 24 h depois da última mensagem do
 * contato; depois, modelo aprovado ({@code dados.modelo} da mensagem). Anexos sobem para a Meta antes do envio
 * (POST /{phone-number-id}/media) e vão pelo id.
 *
 * <p>Coexistência: o mesmo número pode continuar no aplicativo WhatsApp Business. O que o atendente responde pelo
 * celular chega pelo webhook {@code smb_message_echoes} e entra na conversa (ver EntradaWhatsapp).
 */
@Component
public class CanalWhatsappCloud implements Canal {

    @Resource
    private ProvedorHttp http;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private ArquivosCanal arquivos;
    @Resource
    private AtendimentoProperties props;

    @Override
    public String tipo() {
        return "whatsapp_cloud";
    }

    @Override
    public String enviar(CaixaDO caixa, ConversaDO conversa, ContatoDO contato, ContatoCanalDO endereco, MensagemDO m) throws Exception {
        CanalSegredos.Config cfg = exigir(caixa);
        String para = endereco.getOrigemId();
        Map<String, Object> dados = m.getDados() == null ? Map.of() : JsonUtils.parseObject(m.getDados(), new TypeReference<Map<String, Object>>() {
        });
        if (dados.get("modelo") instanceof Map<?, ?> modelo) {
            return mandar(cfg, mensagem(para, "template", corpoModelo(modelo)));
        }
        List<AtendimentoVOs.Anexo> anexos = JsonUtils.parseObject(m.getAnexos(), new TypeReference<List<AtendimentoVOs.Anexo>>() {
        });
        String texto = m.getConteudo() == null ? "" : m.getConteudo().strip();
        String id = null;
        boolean legendaUsada = false;
        for (AtendimentoVOs.Anexo a : anexos == null ? List.<AtendimentoVOs.Anexo>of() : anexos) {
            ArquivosCanal.Local arq = arquivos.ler(a);
            String tipo = Meta.tipoMidiaWhatsapp(arq.tipo());
            String mediaId = subir(cfg, arq);
            Map<String, Object> midia = new LinkedHashMap<>();
            midia.put("id", mediaId);
            if ("document".equals(tipo)) {
                midia.put("filename", arq.nome());
            }
            if (!legendaUsada && !texto.isEmpty() && !"audio".equals(tipo)) {
                midia.put("caption", texto);
                legendaUsada = true;
            }
            id = mandar(cfg, mensagem(para, tipo, midia));
        }
        if (!texto.isEmpty() && !legendaUsada) {
            id = mandar(cfg, mensagem(para, "text", Map.of("body", texto, "preview_url", false)));
        }
        return id;
    }

    @Override
    public void avisarLeitura(CaixaDO caixa, ContatoCanalDO endereco, MensagemDO entrada) throws Exception {
        CanalSegredos.Config cfg = exigir(caixa);
        if (entrada.getIdExterno() == null) {
            return;
        }
        Map<String, Object> corpo = Map.of("messaging_product", "whatsapp", "status", "read", "message_id", entrada.getIdExterno());
        ProvedorHttp.Resposta r = http.postJson(props.getMetaGraphUrl() + "/" + cfg.phoneNumberId() + "/messages", cfg.token(), corpo);
        if (!r.ok()) {
            throw erro(r);
        }
    }

    @Override
    public Map<String, Object> conectar(CaixaDO caixa) throws Exception {
        CanalSegredos.Config cfg = exigir(caixa);
        if (cfg.wabaId() == null) {
            throw new FalhaDefinitiva("informe o identificador da conta do WhatsApp Business (WABA)");
        }
        ProvedorHttp.Resposta numero = http.get(props.getMetaGraphUrl() + "/" + cfg.phoneNumberId()
                + "?fields=display_phone_number,verified_name,quality_rating,platform_type,code_verification_status,name_status", cfg.token());
        if (!numero.ok()) {
            throw erro(numero);
        }
        // assina o app na conta: sem isso a Meta não manda os webhooks deste número
        ProvedorHttp.Resposta assinatura = http.postJson(props.getMetaGraphUrl() + "/" + cfg.wabaId() + "/subscribed_apps", cfg.token(), Map.of());
        if (!assinatura.ok()) {
            throw erro(assinatura);
        }
        Map<String, Object> info = new LinkedHashMap<>();
        for (String campo : List.of("display_phone_number", "verified_name", "quality_rating", "platform_type", "code_verification_status", "name_status")) {
            if (numero.json().get(campo) != null) {
                info.put(campo, numero.json().get(campo));
            }
        }
        info.put("assinado", true);
        return info;
    }

    /** Modelos da conta (para a sincronização), seguindo a paginação da Meta. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> modelos(CaixaDO caixa) throws Exception {
        CanalSegredos.Config cfg = exigir(caixa);
        if (cfg.wabaId() == null) {
            throw new FalhaDefinitiva("informe o identificador da conta do WhatsApp Business (WABA)");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        String url = props.getMetaGraphUrl() + "/" + cfg.wabaId() + "/message_templates?fields=name,language,status,category,components,parameter_format&limit=100";
        String host = java.net.URI.create(props.getMetaGraphUrl()).getHost();
        for (int pagina = 0; url != null && pagina < 50; pagina++) {
            ProvedorHttp.Resposta r = http.get(url, cfg.token());
            if (!r.ok()) {
                throw erro(r);
            }
            if (r.json().get("data") instanceof List<?> lista) {
                lista.stream().filter(Map.class::isInstance).forEach(x -> out.add((Map<String, Object>) x));
            }
            url = null;
            if (r.json().get("paging") instanceof Map<?, ?> p && p.get("next") instanceof String prox) {
                // só segue a paginação no próprio endereço da API (o token vai junto)
                url = host.equalsIgnoreCase(java.net.URI.create(prox).getHost()) ? prox : null;
            }
        }
        return out;
    }

    /** Endereço temporário de uma mídia recebida (GET /{media-id}) e o tipo dela. */
    public Map<String, Object> midia(CanalSegredos.Config cfg, String mediaId) throws Exception {
        ProvedorHttp.Resposta r = http.get(props.getMetaGraphUrl() + "/" + mediaId, cfg.token());
        if (!r.ok()) {
            throw erro(r);
        }
        return r.json();
    }

    CanalSegredos.Config exigir(CaixaDO caixa) {
        CanalSegredos.Config cfg = segredos.ler(caixa);
        if (cfg.phoneNumberId() == null || cfg.token() == null) {
            throw new FalhaDefinitiva("a caixa não tem o número e o token do WhatsApp configurados");
        }
        return cfg;
    }

    private String subir(CanalSegredos.Config cfg, ArquivosCanal.Local arq) throws Exception {
        ProvedorHttp.Resposta r = http.postArquivo(props.getMetaGraphUrl() + "/" + cfg.phoneNumberId() + "/media", cfg.token(),
                Map.of("messaging_product", "whatsapp", "type", arq.tipo()), "file", arq.dados(), arq.nome(), arq.tipo());
        if (!r.ok() || r.json().get("id") == null) {
            throw erro(r);
        }
        return r.json().get("id").toString();
    }

    private String mandar(CanalSegredos.Config cfg, Map<String, Object> corpo) throws Exception {
        ProvedorHttp.Resposta r = http.postJson(props.getMetaGraphUrl() + "/" + cfg.phoneNumberId() + "/messages", cfg.token(), corpo);
        if (!r.ok()) {
            throw erro(r);
        }
        if (r.json().get("messages") instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> msg && msg.get("id") != null) {
            return msg.get("id").toString();
        }
        return null;
    }

    private static Map<String, Object> mensagem(String para, String tipo, Object conteudo) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("messaging_product", "whatsapp");
        c.put("recipient_type", "individual");
        c.put("to", para);
        c.put("type", tipo);
        c.put(tipo, conteudo);
        return c;
    }

    /** {"nome", "idioma", "parametros": [...], "nomesVariaveis": [...]} → componente "template" da Cloud API */
    static Map<String, Object> corpoModelo(Map<?, ?> modelo) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", modelo.get("nome"));
        t.put("language", Map.of("code", modelo.get("idioma")));
        List<?> params = modelo.get("parametros") instanceof List<?> l ? l : List.of();
        List<?> nomes = modelo.get("nomesVariaveis") instanceof List<?> l ? l : List.of();
        if (!params.isEmpty()) {
            List<Map<String, Object>> p = new ArrayList<>();
            for (int i = 0; i < params.size(); i++) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("type", "text");
                if (i < nomes.size()) {
                    x.put("parameter_name", nomes.get(i));
                }
                x.put("text", String.valueOf(params.get(i)));
                p.add(x);
            }
            t.put("components", List.of(Map.of("type", "body", "parameters", p)));
        }
        return t;
    }

    static RuntimeException erro(ProvedorHttp.Resposta r) {
        Meta.Erro e = Meta.classificar(r.status(), r.json());
        return e.passageiro() ? new IllegalStateException(e.mensagem()) : new FalhaDefinitiva(e.mensagem());
    }

}
