package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.canal.ArquivosCanal;
import cn.iocoder.yudao.module.atendimento.canal.Canal;
import cn.iocoder.yudao.module.atendimento.canal.CanalSegredos;
import cn.iocoder.yudao.module.atendimento.canal.CanalWhatsappCloud;
import cn.iocoder.yudao.module.atendimento.canal.ProvedorHttp;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dominio.Meta;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

/**
 * O que chega pelo webhook do WhatsApp Cloud API: mensagens do contato ({@code messages}), situação das mensagens
 * enviadas ({@code statuses}) e, na coexistência com o aplicativo WhatsApp Business, o que o atendente respondeu pelo
 * celular ({@code smb_message_echoes}), o histórico das conversas trazido na conexão ({@code history}) e os
 * contatos da agenda do aplicativo ({@code smb_app_state_sync}).
 */
@Slf4j
@Service
public class EntradaWhatsapp {

    /** Hosts de onde a Meta serve as mídias (o endereço vem da própria API, mas só esses recebem o token) */
    private static final List<String> HOSTS_MIDIA = List.of("fbsbx.com", "facebook.com", "whatsapp.net", "fbcdn.net");

    @Resource
    private ConversaService conversaService;
    @Resource
    private ContatoService contatoService;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private CanalWhatsappCloud canal;
    @Resource
    private ProvedorHttp http;
    @Resource
    private ArquivosCanal arquivos;
    @Resource
    private AtendimentoProperties props;

    @SuppressWarnings("unchecked")
    public void processar(CaixaDO caixa, Map<String, Object> corpo) throws Exception {
        CanalSegredos.Config cfg = segredos.ler(caixa);
        for (Map<String, Object> entrada : mapas(corpo.get("entry"))) {
            for (Map<String, Object> mudanca : mapas(entrada.get("changes"))) {
                String campo = String.valueOf(mudanca.get("field"));
                Map<String, Object> valor = mudanca.get("value") instanceof Map<?, ?> v ? (Map<String, Object>) v : Map.of();
                String empresa = valor.get("metadata") instanceof Map<?, ?> meta && meta.get("display_phone_number") != null
                        ? meta.get("display_phone_number").toString().replaceAll("\\D", "") : cfg.numeroEmpresa();
                switch (campo) {
                    case "messages" -> {
                        Map<String, String> nomes = new HashMap<>();
                        for (Map<String, Object> c : mapas(valor.get("contacts"))) {
                            if (c.get("profile") instanceof Map<?, ?> p && p.get("name") != null) {
                                nomes.put(String.valueOf(c.get("wa_id")), p.get("name").toString());
                            }
                        }
                        for (Map<String, Object> m : mapas(valor.get("messages"))) {
                            String de = String.valueOf(m.get("from"));
                            receber(caixa, cfg, m, de, nomes.get(de), false, false);
                        }
                        for (Map<String, Object> s : mapas(valor.get("statuses"))) {
                            situacao(caixa, s);
                        }
                    }
                    case "smb_message_echoes" -> {
                        for (Map<String, Object> m : mapas(valor.get("message_echoes"))) {
                            receber(caixa, cfg, m, String.valueOf(m.get("to")), null, true, false);
                        }
                    }
                    case "history" -> historico(caixa, cfg, valor, empresa);
                    case "smb_app_state_sync" -> agenda(caixa, valor);
                    default -> log.info("[processar][caixa {}: campo {} ignorado]", caixa.getId(), campo);
                }
            }
        }
    }

    private void historico(CaixaDO caixa, CanalSegredos.Config cfg, Map<String, Object> valor, String empresa) throws Exception {
        for (Map<String, Object> h : mapas(valor.get("history"))) {
            if (h.get("errors") instanceof List<?> erros && !erros.isEmpty()) {
                // o dono do número recusou compartilhar o histórico no aplicativo
                log.info("[historico][caixa {}: histórico não compartilhado: {}]", caixa.getId(), erros);
                continue;
            }
            for (Map<String, Object> conversa : mapas(h.get("threads"))) {
                String contato = String.valueOf(conversa.get("id"));
                for (Map<String, Object> m : mapas(conversa.get("messages"))) {
                    String de = String.valueOf(m.get("from"));
                    boolean daEmpresa = empresa != null ? empresa.equals(de) : !contato.equals(de);
                    receber(caixa, cfg, m, contato, null, daEmpresa, true);
                }
            }
        }
    }

    /** Contatos da agenda do aplicativo (coexistência): cria ou completa o contato com o nome salvo no celular. */
    private void agenda(CaixaDO caixa, Map<String, Object> valor) {
        for (Map<String, Object> s : mapas(valor.get("state_sync"))) {
            if (!"contact".equals(s.get("type")) || "remove".equals(s.get("action")) || !(s.get("contact") instanceof Map<?, ?> c)) {
                continue;
            }
            String numero = c.get("phone_number") == null ? "" : c.get("phone_number").toString().replaceAll("\\D", "");
            if (numero.length() < 8) {
                continue;
            }
            Object nome = c.get("full_name") != null ? c.get("full_name") : c.get("first_name");
            contatoService.encontrarOuCriar(caixa.getId(), numero,
                    new ContatoService.Origem(nome == null ? null : nome.toString(), "+" + numero, null, null));
        }
    }

    @SuppressWarnings("unchecked")
    private void receber(CaixaDO caixa, CanalSegredos.Config cfg, Map<String, Object> m, String origemId, String nome,
                         boolean eco, boolean historico) throws Exception {
        if (origemId == null || "null".equals(origemId)) {
            return;
        }
        String tipo = String.valueOf(m.get("type"));
        String conteudo = "";
        String formato = "texto";
        List<AtendimentoVOs.Anexo> anexos = new ArrayList<>();
        Map<String, Object> dados = new LinkedHashMap<>();
        Map<String, Object> corpo = m.get(tipo) instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
        switch (tipo) {
            case "text" -> conteudo = texto(corpo.get("body"));
            case "image", "video", "audio", "document", "sticker" -> {
                conteudo = texto(corpo.get("caption"));
                formato = "sticker".equals(tipo) ? "imagem" : Meta.formato(texto(corpo.get("mime_type")));
                if ("document".equals(tipo)) {
                    formato = "arquivo";
                }
                if ("sticker".equals(tipo)) {
                    dados.put("figurinha", true);
                }
                if (Boolean.TRUE.equals(corpo.get("voice"))) {
                    dados.put("voz", true);
                }
                if (historico) {
                    conteudo = (conteudo + " [mídia do histórico não importada]").strip();
                } else {
                    try {
                        anexos.add(baixar(cfg, texto(corpo.get("id")), texto(corpo.get("mime_type")), texto(corpo.get("filename")), tipo));
                    } catch (Canal.FalhaDefinitiva f) {
                        conteudo = (conteudo + " [a mídia não pôde ser baixada: " + f.getMessage() + "]").strip();
                    }
                }
            }
            case "location" -> {
                Object lat = corpo.get("latitude");
                Object lng = corpo.get("longitude");
                String lugar = String.join(" — ", Stream.of(texto(corpo.get("name")), texto(corpo.get("address"))).filter(s -> !s.isEmpty()).toList());
                conteudo = ("📍 " + lugar).strip() + "\nhttps://www.google.com/maps?q=" + lat + "," + lng;
                formato = "localizacao";
                dados.put("localizacao", Map.of("latitude", String.valueOf(lat), "longitude", String.valueOf(lng), "nome", lugar));
            }
            case "contacts" -> {
                List<String> linhas = new ArrayList<>();
                for (Map<String, Object> c : mapas(m.get("contacts"))) {
                    String n = c.get("name") instanceof Map<?, ?> nm ? texto(nm.get("formatted_name")) : "";
                    List<String> tels = mapas(c.get("phones")).stream().map(p -> texto(p.get("phone"))).filter(s -> !s.isEmpty()).toList();
                    linhas.add("👤 " + n + (tels.isEmpty() ? "" : ": " + String.join(", ", tels)));
                }
                conteudo = String.join("\n", linhas);
                dados.put("contatos", m.get("contacts"));
            }
            case "interactive" -> {
                Object r = corpo.get("button_reply") != null ? corpo.get("button_reply") : corpo.get("list_reply");
                if (r instanceof Map<?, ?> resp) {
                    conteudo = texto(resp.get("title"));
                    dados.put("resposta", Map.of("id", texto(resp.get("id")), "titulo", conteudo));
                }
            }
            case "button" -> {
                conteudo = texto(corpo.get("text"));
                dados.put("botao", texto(corpo.get("payload")));
            }
            case "reaction" -> {
                String emoji = texto(corpo.get("emoji"));
                conteudo = emoji.isEmpty() ? "(removeu a reação)" : "Reagiu com " + emoji;
                formato = "reacao";
                dados.put("reacao", Map.of("emoji", emoji, "mensagem", texto(corpo.get("message_id"))));
            }
            case "order" -> {
                conteudo = "🛒 Pedido pelo catálogo";
                dados.put("pedido", corpo);
            }
            case "system" -> conteudo = texto(corpo.get("body"));
            default -> conteudo = "[mensagem de um tipo que o CO-TI ainda não mostra: " + tipo + "]";
        }
        if (m.get("context") instanceof Map<?, ?> ctx && ctx.get("id") != null) {
            dados.put("respondeA", ctx.get("id").toString());
        }
        if (m.get("referral") instanceof Map<?, ?> anuncio) {
            dados.put("anuncio", anuncio);
        }
        if (eco) {
            dados.put("origem", "celular");
        }
        if (historico) {
            dados.put("historico", true);
        }
        LocalDateTime quando = null;
        if (m.get("timestamp") != null) {
            try {
                quando = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(m.get("timestamp").toString())), ZoneId.systemDefault());
            } catch (NumberFormatException ignorado) {
                // sem data: vale a de agora
            }
        }
        ContatoService.Origem contato = new ContatoService.Origem(nome, "+" + origemId.replaceAll("\\D", ""), null, null);
        conversaService.receber(caixa, new ConversaService.Recebida(origemId, contato, conteudo, formato, anexos,
                texto(m.get("id")).isEmpty() ? null : texto(m.get("id")), dados, eco, quando, historico));
    }

    /** Situação de uma mensagem enviada (enviada, entregue, lida, falhou). */
    private void situacao(CaixaDO caixa, Map<String, Object> s) {
        String status = Meta.situacao(texto(s.get("status")));
        if (status == null || "enviada".equals(status)) {
            return;   // "sent" já fica registrado quando a fila entrega
        }
        String erro = null;
        List<Map<String, Object>> erros = mapas(s.get("errors"));
        if (!erros.isEmpty()) {
            Meta.Erro e = Meta.classificar(400, Map.of("error", Map.of("code", erros.get(0).getOrDefault("code", 0),
                    "message", erros.get(0).getOrDefault("title", erros.get(0).getOrDefault("message", "erro")),
                    "error_data", erros.get(0).getOrDefault("error_data", Map.of()))));
            erro = e.mensagem();
        }
        if (!conversaService.entregaPeloExterno(caixa.getId(), texto(s.get("id")), status, erro)) {
            // aviso antes de o envio gravar o id (corrida rara): tenta de novo daqui a pouco
            long ts = s.get("timestamp") == null ? 0 : Long.parseLong(s.get("timestamp").toString());
            if (Instant.now().getEpochSecond() - ts < 120) {
                throw new IllegalStateException("situação de mensagem ainda sem id gravado: " + s.get("id"));
            }
        }
    }

    private AtendimentoVOs.Anexo baixar(CanalSegredos.Config cfg, String mediaId, String mime, String nome, String prefixo) throws Exception {
        if (mediaId.isEmpty()) {
            throw new Canal.FalhaDefinitiva("sem identificador da mídia");
        }
        Map<String, Object> info = canal.midia(cfg, mediaId);
        String url = texto(info.get("url"));
        String host = url.isEmpty() ? "" : Optional.ofNullable(URI.create(url).getHost()).orElse("").toLowerCase(Locale.ROOT);
        String hostApi = Optional.ofNullable(URI.create(props.getMetaGraphUrl()).getHost()).orElse("");
        boolean permitido = host.equalsIgnoreCase(hostApi) || HOSTS_MIDIA.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        if (!permitido) {
            throw new Canal.FalhaDefinitiva("endereço de mídia inesperado");
        }
        ProvedorHttp.Arquivo a = http.baixar(url, cfg.token(), props.getMidiaMaxima());
        String tipo = !texto(info.get("mime_type")).isEmpty() ? texto(info.get("mime_type")) : mime.isEmpty() ? a.tipo() : mime;
        return arquivos.salvar(a.dados(), nome, tipo, prefixo);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mapas(Object lista) {
        if (!(lista instanceof List<?> l)) {
            return List.of();
        }
        return l.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList();
    }

    static String texto(Object o) {
        return o == null ? "" : o.toString();
    }

}
