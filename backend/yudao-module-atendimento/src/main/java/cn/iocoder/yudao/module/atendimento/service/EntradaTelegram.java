package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.canal.ArquivosCanal;
import cn.iocoder.yudao.module.atendimento.canal.Canal;
import cn.iocoder.yudao.module.atendimento.canal.CanalSegredos;
import cn.iocoder.yudao.module.atendimento.canal.CanalTelegram;
import cn.iocoder.yudao.module.atendimento.canal.ProvedorHttp;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dominio.Meta;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static cn.iocoder.yudao.module.atendimento.service.EntradaWhatsapp.mapas;
import static cn.iocoder.yudao.module.atendimento.service.EntradaWhatsapp.texto;

/**
 * O que chega pelo webhook do Telegram: mensagens das conversas privadas com o robô (grupos e canais são ignorados).
 */
@Slf4j
@Service
public class EntradaTelegram {

    @Resource
    private ConversaService conversaService;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private CanalTelegram canal;
    @Resource
    private ProvedorHttp http;
    @Resource
    private ArquivosCanal arquivos;
    @Resource
    private AtendimentoProperties props;

    @SuppressWarnings("unchecked")
    public void processar(CaixaDO caixa, Map<String, Object> update) throws Exception {
        if (!(update.get("message") instanceof Map<?, ?> mm)) {
            return;   // edições, botões etc. ficam de fora (allowed_updates = message)
        }
        Map<String, Object> m = (Map<String, Object>) mm;
        Map<String, Object> chat = m.get("chat") instanceof Map<?, ?> c ? (Map<String, Object>) c : Map.of();
        if (!"private".equals(chat.get("type"))) {
            log.info("[processar][caixa {}: mensagem de {} ignorada]", caixa.getId(), chat.get("type"));
            return;
        }
        String chatId = texto(chat.get("id"));
        Map<String, Object> de = m.get("from") instanceof Map<?, ?> f ? (Map<String, Object>) f : Map.of();
        String nome = String.join(" ", List.of(texto(de.get("first_name")), texto(de.get("last_name")))).strip();
        if (nome.isEmpty() && !texto(de.get("username")).isEmpty()) {
            nome = "@" + de.get("username");
        }
        String telefone = null;
        StringBuilder conteudo = new StringBuilder(!texto(m.get("text")).isEmpty() ? texto(m.get("text")) : texto(m.get("caption")));
        String formato = "texto";
        List<AtendimentoVOs.Anexo> anexos = new ArrayList<>();
        Map<String, Object> dados = new LinkedHashMap<>();
        // arquivo: a maior das fotos, ou o documento/áudio/vídeo
        String fileId = null;
        String nomeArquivo = null;
        String mime = null;
        String prefixo = "arquivo";
        if (m.get("photo") instanceof List<?> fotos && !fotos.isEmpty()) {
            Map<String, Object> maior = mapas(fotos).stream().max(Comparator.comparingLong(x -> x.get("file_size") instanceof Number n ? n.longValue() : 0)).orElse(Map.of());
            fileId = texto(maior.get("file_id"));
            mime = "image/jpeg";
            prefixo = "imagem";
        } else {
            for (String tipo : List.of("document", "voice", "audio", "video", "video_note", "animation")) {
                if (m.get(tipo) instanceof Map<?, ?> arq) {
                    fileId = texto(arq.get("file_id"));
                    nomeArquivo = arq.get("file_name") == null ? null : arq.get("file_name").toString();
                    mime = arq.get("mime_type") == null ? ("voice".equals(tipo) ? "audio/ogg" : null) : arq.get("mime_type").toString();
                    prefixo = tipo;
                    if ("voice".equals(tipo)) {
                        dados.put("voz", true);
                    }
                    break;
                }
            }
        }
        if (fileId != null && !fileId.isEmpty()) {
            try {
                String token = segredos.ler(caixa).botToken();
                String caminho = canal.caminhoArquivo(token, fileId);
                ProvedorHttp.Arquivo a = http.baixar(canal.enderecoArquivo(token, caminho), null, Math.min(props.getMidiaMaxima(), 20L * 1024 * 1024));
                String tipo = mime != null ? mime : a.tipo();
                anexos.add(arquivos.salvar(a.dados(), nomeArquivo, tipo, prefixo));
                formato = "document".equals(prefixo) ? "arquivo" : Meta.formato(tipo);
            } catch (Canal.FalhaDefinitiva f) {
                conteudo.append(conteudo.isEmpty() ? "" : "\n").append("[o arquivo não pôde ser baixado: ").append(f.getMessage()).append("]");
            }
        }
        if (m.get("sticker") instanceof Map<?, ?> st) {
            conteudo.append(conteudo.isEmpty() ? "" : " ").append("[figurinha ").append(texto(st.get("emoji"))).append("]");
            dados.put("figurinha", true);
        }
        if (m.get("location") instanceof Map<?, ?> loc) {
            conteudo.append(conteudo.isEmpty() ? "" : "\n").append("📍 https://www.google.com/maps?q=").append(loc.get("latitude")).append(",").append(loc.get("longitude"));
            formato = "localizacao";
            dados.put("localizacao", Map.of("latitude", texto(loc.get("latitude")), "longitude", texto(loc.get("longitude"))));
        }
        if (m.get("contact") instanceof Map<?, ?> ct) {
            String tel = texto(ct.get("phone_number"));
            conteudo.append(conteudo.isEmpty() ? "" : "\n").append("👤 ").append(texto(ct.get("first_name"))).append(": ").append(tel);
            // o próprio contato compartilhou o número: liga o telefone ao contato
            if (ct.get("user_id") != null && texto(ct.get("user_id")).equals(texto(de.get("id")))) {
                telefone = tel;
            }
        }
        if (m.get("reply_to_message") instanceof Map<?, ?> r && r.get("message_id") != null) {
            dados.put("respondeA", chatId + ":" + r.get("message_id"));
        }
        if (conteudo.isEmpty() && anexos.isEmpty()) {
            conteudo.append("[mensagem de um tipo que o CO-TI ainda não mostra]");
        }
        LocalDateTime quando = m.get("date") instanceof Number n ? LocalDateTime.ofInstant(Instant.ofEpochSecond(n.longValue()), ZoneId.systemDefault()) : null;
        conversaService.receber(caixa, new ConversaService.Recebida(chatId,
                new ContatoService.Origem(nome.isEmpty() ? null : nome, telefone, null, null), conteudo.toString(), formato, anexos,
                chatId + ":" + texto(m.get("message_id")), dados, false, quando, false));
    }

}
