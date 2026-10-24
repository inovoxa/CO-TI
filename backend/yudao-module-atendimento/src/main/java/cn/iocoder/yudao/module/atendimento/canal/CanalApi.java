package cn.iocoder.yudao.module.atendimento.canal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dominio.Webhooks;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canal "API" (Chatwoot: Channel::Api): um sistema qualquer manda as mensagens do contato para o CO-TI e recebe as
 * respostas num webhook. As respostas vão assinadas (cabeçalho {@code X-CO-TI-Assinatura: sha256=...}, HMAC-SHA256
 * do corpo com o segredo da caixa).
 */
@Component
public class CanalApi implements Canal {

    public static final String CONTEXTO_SEGREDO = "atendimento-webhook";

    /** Sem seguir redirecionamento: o destino é só o endereço conferido. */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private AtendimentoProperties props;

    @Override
    public String tipo() {
        return "api";
    }

    @Override
    public String enviar(CaixaDO caixa, ConversaDO conversa, ContatoDO contato, ContatoCanalDO endereco, MensagemDO m) throws Exception {
        if (caixa.getWebhookUrl() == null || caixa.getWebhookUrl().isBlank()) {
            throw new FalhaDefinitiva("a caixa não tem endereço de webhook para as respostas");
        }
        String proibida = Webhooks.motivoUrlProibida(caixa.getWebhookUrl());
        if (proibida != null) {
            throw new FalhaDefinitiva("webhook: " + proibida);
        }
        String base = props.getUrlPublica() == null ? "" : props.getUrlPublica().replaceAll("/+$", "");
        List<AtendimentoVOs.Anexo> anexos = JsonUtils.parseObject(m.getAnexos(), new TypeReference<List<AtendimentoVOs.Anexo>>() {
        });
        anexos.forEach(a -> {
            if (a.getUrl() != null && a.getUrl().startsWith("/")) {
                a.setUrl(base + a.getUrl());
            }
        });
        Map<String, Object> contatoJson = new LinkedHashMap<>();
        contatoJson.put("id", contato.getId());
        contatoJson.put("nome", contato.getNome());
        contatoJson.put("telefone", contato.getTelefone());
        contatoJson.put("email", contato.getEmail());
        contatoJson.put("identificador", contato.getIdentificador());
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", m.getId());
        msg.put("conteudo", m.getConteudo());
        msg.put("anexos", anexos);
        msg.put("remetente", m.getRemetenteTipo());
        msg.put("criadaEm", m.getCreateTime() == null ? null : m.getCreateTime().toString());
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("evento", "mensagem");
        corpo.put("caixaId", caixa.getId());
        corpo.put("conversaId", conversa.getId());
        corpo.put("origemId", endereco.getOrigemId());
        corpo.put("contato", contatoJson);
        corpo.put("mensagem", msg);
        byte[] bytes = JsonUtils.toJsonString(corpo).getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(caixa.getWebhookUrl())).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8").header("X-CO-TI-Evento", "mensagem")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (caixa.getWebhookSegredo() != null) {
            req.header("X-CO-TI-Assinatura", "sha256=" + Webhooks.hmacSha256(cifrador.decifrar(caixa.getWebhookSegredo(), CONTEXTO_SEGREDO), bytes));
        }
        HttpResponse<String> r = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400 && r.statusCode() < 500 && r.statusCode() != 408 && r.statusCode() != 429) {
            throw new FalhaDefinitiva("o webhook recusou (HTTP " + r.statusCode() + ")");
        }
        if (r.statusCode() >= 300) {
            throw new IllegalStateException("webhook respondeu HTTP " + r.statusCode());
        }
        // o sistema pode devolver o id que deu à mensagem: {"idExterno": "..."}
        try {
            Map<String, Object> resp = JsonUtils.parseObject(r.body(), new TypeReference<Map<String, Object>>() {
            });
            Object id = resp == null ? null : resp.get("idExterno");
            return id == null ? null : String.valueOf(id);
        } catch (Exception semJson) {
            return null;
        }
    }

}
