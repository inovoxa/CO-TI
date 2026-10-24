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
 * Telegram (Bot API). Só conversas privadas com o robô. O texto vai sem formatação do Telegram (sem parse_mode), para
 * que nada digitado vire marcação. O id externo é {@code chat:mensagem}, porque o número da mensagem é por conversa.
 */
@Component
public class CanalTelegram implements Canal {

    private static final int LEGENDA_MAXIMA = 1024;

    @Resource
    private ProvedorHttp http;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private AtendimentoProperties props;
    @Resource
    private ArquivosCanal arquivos;

    @Override
    public String tipo() {
        return "telegram";
    }

    @Override
    public String enviar(CaixaDO caixa, ConversaDO conversa, ContatoDO contato, ContatoCanalDO endereco, MensagemDO m) throws Exception {
        String token = token(caixa);
        String chat = endereco.getOrigemId();
        List<AtendimentoVOs.Anexo> anexos = JsonUtils.parseObject(m.getAnexos(), new TypeReference<List<AtendimentoVOs.Anexo>>() {
        });
        String texto = m.getConteudo() == null ? "" : m.getConteudo().strip();
        String id = null;
        boolean legendaUsada = false;
        for (AtendimentoVOs.Anexo a : anexos == null ? List.<AtendimentoVOs.Anexo>of() : anexos) {
            ArquivosCanal.Local arq = arquivos.ler(a);
            String t = arq.tipo().toLowerCase(Locale.ROOT);
            String[] metodo = t.equals("image/jpeg") || t.equals("image/png") ? new String[]{"sendPhoto", "photo"}
                    : t.equals("video/mp4") ? new String[]{"sendVideo", "video"}
                    : t.equals("audio/mpeg") || t.equals("audio/mp4") ? new String[]{"sendAudio", "audio"}
                    : t.equals("audio/ogg") ? new String[]{"sendVoice", "voice"} : new String[]{"sendDocument", "document"};
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("chat_id", chat);
            if (!legendaUsada && !texto.isEmpty() && texto.length() <= LEGENDA_MAXIMA) {
                campos.put("caption", texto);
                legendaUsada = true;
            }
            id = resultado(chat, http.postArquivo(base(token) + "/" + metodo[0], null, campos, metodo[1], arq.dados(), arq.nome(), arq.tipo()));
        }
        if (!texto.isEmpty() && !legendaUsada) {
            id = resultado(chat, http.postJson(base(token) + "/sendMessage", null, Map.of("chat_id", chat, "text", texto)));
        }
        return id;
    }

    /**
     * Confere o token (getMe) e cadastra o webhook com o segredo que o Telegram devolve em cada chamada.
     *
     * @param endereco    endereço público do webhook desta caixa
     * @param secretToken segredo novo (já gravado cifrado pela configuração)
     */
    public Map<String, Object> conectar(CaixaDO caixa, String endereco, String secretToken) throws Exception {
        String token = token(caixa);
        ProvedorHttp.Resposta eu = http.get(base(token) + "/getMe", null);
        if (!eu.ok() || !Boolean.TRUE.equals(eu.json().get("ok"))) {
            throw erro(eu);
        }
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("url", endereco);
        corpo.put("secret_token", secretToken);
        corpo.put("allowed_updates", List.of("message"));
        ProvedorHttp.Resposta wh = http.postJson(base(token) + "/setWebhook", null, corpo);
        if (!wh.ok() || !Boolean.TRUE.equals(wh.json().get("ok"))) {
            throw erro(wh);
        }
        Map<String, Object> info = new LinkedHashMap<>();
        if (eu.json().get("result") instanceof Map<?, ?> r) {
            info.put("id", r.get("id"));
            info.put("username", r.get("username"));
            info.put("first_name", r.get("first_name"));
        }
        info.put("webhook", endereco);
        return info;
    }

    /** Caminho de um arquivo recebido (getFile) para baixar em /file/bot{token}/{caminho}. */
    public String caminhoArquivo(String token, String fileId) throws Exception {
        ProvedorHttp.Resposta r = http.postJson(base(token) + "/getFile", null, Map.of("file_id", fileId));
        if (!r.ok() || !(r.json().get("result") instanceof Map<?, ?> res) || res.get("file_path") == null) {
            throw erro(r);
        }
        return res.get("file_path").toString();
    }

    public String enderecoArquivo(String token, String caminho) {
        return props.getTelegramUrl().replaceAll("/+$", "") + "/file/bot" + token + "/" + caminho;
    }

    String token(CaixaDO caixa) {
        String t = segredos.ler(caixa).botToken();
        if (t == null) {
            throw new FalhaDefinitiva("a caixa não tem o token do robô do Telegram configurado");
        }
        return t;
    }

    private String base(String token) {
        return props.getTelegramUrl().replaceAll("/+$", "") + "/bot" + token;
    }

    private static String resultado(String chat, ProvedorHttp.Resposta r) {
        if (!r.ok() || !Boolean.TRUE.equals(r.json().get("ok"))) {
            throw erro(r);
        }
        if (r.json().get("result") instanceof Map<?, ?> res && res.get("message_id") != null) {
            return chat + ":" + res.get("message_id");
        }
        return null;
    }

    static RuntimeException erro(ProvedorHttp.Resposta r) {
        Object desc = r.json().get("description");
        String msg = "Telegram: " + (desc == null ? "HTTP " + r.status() : desc);
        int codigo = r.json().get("error_code") instanceof Number n ? n.intValue() : r.status();
        if (codigo == 429 || codigo >= 500) {
            return new IllegalStateException(msg);
        }
        if (codigo == 403) {
            return new FalhaDefinitiva("O contato bloqueou o robô ou não iniciou a conversa (" + msg + ")");
        }
        if (codigo == 401 || codigo == 404) {
            return new FalhaDefinitiva("Token do robô inválido: atualize na configuração da caixa (" + msg + ")");
        }
        return new FalhaDefinitiva(msg);
    }

}
