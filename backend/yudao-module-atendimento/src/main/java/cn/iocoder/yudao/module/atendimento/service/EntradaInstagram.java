package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.canal.ArquivosCanal;
import cn.iocoder.yudao.module.atendimento.canal.Canal;
import cn.iocoder.yudao.module.atendimento.canal.CanalInstagram;
import cn.iocoder.yudao.module.atendimento.canal.CanalSegredos;
import cn.iocoder.yudao.module.atendimento.canal.ProvedorHttp;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ContatoCanalDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ContatoCanalMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static cn.iocoder.yudao.module.atendimento.service.EntradaWhatsapp.mapas;
import static cn.iocoder.yudao.module.atendimento.service.EntradaWhatsapp.texto;

/**
 * O que chega pelo webhook do Instagram (Direct): mensagens, ecos (o que a empresa mandou, inclusive pelo app do
 * Instagram), reações, respostas de botão e avisos de leitura.
 */
@Slf4j
@Service
public class EntradaInstagram {

    /** Onde o Instagram guarda as mídias das mensagens (links assinados, sem token) */
    private static final List<String> HOSTS_MIDIA = List.of("fbcdn.net", "cdninstagram.com", "fbsbx.com", "facebook.com", "instagram.com");

    @Resource
    private ConversaService conversaService;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private CanalInstagram canal;
    @Resource
    private ContatoCanalMapper contatoCanalMapper;
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
            for (Map<String, Object> ev : mapas(entrada.get("messaging"))) {
                String remetente = ev.get("sender") instanceof Map<?, ?> s ? texto(s.get("id")) : "";
                String destino = ev.get("recipient") instanceof Map<?, ?> r ? texto(r.get("id")) : "";
                LocalDateTime quando = ev.get("timestamp") instanceof Number n
                        ? LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), ZoneId.systemDefault()) : null;
                if (ev.get("read") instanceof Map<?, ?> lida) {
                    conversaService.entregaPeloExterno(caixa.getId(), texto(lida.get("mid")), "lida", null);
                    continue;
                }
                if (ev.get("message") instanceof Map<?, ?> msg) {
                    mensagem(caixa, cfg, (Map<String, Object>) msg, remetente, destino, quando);
                } else if (ev.get("reaction") instanceof Map<?, ?> reacao) {
                    boolean desfez = "unreact".equals(reacao.get("action"));
                    String emoji = texto(reacao.get("emoji"));
                    Map<String, Object> dados = new LinkedHashMap<>();
                    dados.put("reacao", Map.of("emoji", emoji, "mensagem", texto(reacao.get("mid"))));
                    receber(caixa, cfg, remetente, desfez ? "(removeu a reação)" : "Reagiu com " + emoji, "reacao", List.of(),
                            "r:" + reacao.get("mid") + ":" + reacao.get("action") + ":" + emoji + ":" + ev.get("timestamp"), dados, false, quando);
                } else if (ev.get("postback") instanceof Map<?, ?> pb) {
                    Map<String, Object> dados = new LinkedHashMap<>();
                    dados.put("botao", texto(pb.get("payload")));
                    receber(caixa, cfg, remetente, texto(pb.get("title")), "texto", List.of(), texto(pb.get("mid")), dados, false, quando);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mensagem(CaixaDO caixa, CanalSegredos.Config cfg, Map<String, Object> msg, String remetente, String destino,
                          LocalDateTime quando) throws Exception {
        if (Boolean.TRUE.equals(msg.get("is_deleted"))) {
            log.info("[mensagem][caixa {}: o contato apagou a mensagem {}]", caixa.getId(), msg.get("mid"));
            return;
        }
        boolean eco = Boolean.TRUE.equals(msg.get("is_echo")) || remetente.equals(cfg.igUserId());
        String contato = eco ? destino : remetente;
        StringBuilder conteudo = new StringBuilder(texto(msg.get("text")));
        String formato = "texto";
        List<AtendimentoVOs.Anexo> anexos = new ArrayList<>();
        Map<String, Object> dados = new LinkedHashMap<>();
        for (Map<String, Object> a : mapas(msg.get("attachments"))) {
            String tipo = texto(a.get("type"));
            String url = a.get("payload") instanceof Map<?, ?> p ? texto(p.get("url")) : "";
            switch (tipo) {
                case "image", "video", "audio", "file" -> {
                    try {
                        AtendimentoVOs.Anexo x = baixar(url, tipo);
                        anexos.add(x);
                        formato = "file".equals(tipo) ? "arquivo" : cn.iocoder.yudao.module.atendimento.dominio.Meta.formato(x.getTipo());
                    } catch (Canal.FalhaDefinitiva f) {
                        conteudo.append(conteudo.isEmpty() ? "" : "\n").append("[a mídia não pôde ser baixada: ").append(f.getMessage()).append("]");
                    }
                }
                case "story_mention" -> conteudo.append(conteudo.isEmpty() ? "" : "\n").append("Mencionou a empresa nos stories").append(link(url));
                default -> conteudo.append(conteudo.isEmpty() ? "" : "\n").append("[").append(tipo).append("]").append(link(url));
            }
        }
        if (msg.get("reply_to") instanceof Map<?, ?> r) {
            if (r.get("mid") != null) {
                dados.put("respondeA", texto(r.get("mid")));
            }
            if (r.get("story") instanceof Map<?, ?> st) {
                dados.put("story", texto(st.get("url")));
                conteudo.insert(0, "(respondendo aos stories) ");
            }
        }
        if (msg.get("quick_reply") instanceof Map<?, ?> qr) {
            dados.put("botao", texto(qr.get("payload")));
        }
        if (Boolean.TRUE.equals(msg.get("is_unsupported")) && conteudo.isEmpty() && anexos.isEmpty()) {
            conteudo.append("[mensagem de um tipo que a API do Instagram não entrega]");
        }
        if (eco) {
            dados.put("origem", "celular");
        }
        receber(caixa, cfg, contato, conteudo.toString(), formato, anexos, texto(msg.get("mid")), dados, eco, quando);
    }

    private void receber(CaixaDO caixa, CanalSegredos.Config cfg, String contato, String conteudo, String formato,
                         List<AtendimentoVOs.Anexo> anexos, String idExterno, Map<String, Object> dados, boolean eco, LocalDateTime quando) {
        if (contato.isEmpty()) {
            return;
        }
        // contato novo: busca nome e usuário no Instagram (uma vez)
        String nome = null;
        boolean conhecido = contatoCanalMapper.selectCount(new LambdaQueryWrapperX<ContatoCanalDO>().eq(ContatoCanalDO::getCaixaId, caixa.getId())
                .eq(ContatoCanalDO::getOrigemId, contato)) > 0;
        if (!conhecido) {
            Map<String, Object> perfil = canal.perfil(cfg, contato);
            String n = texto(perfil.get("name"));
            String u = texto(perfil.get("username"));
            nome = !n.isEmpty() ? (u.isEmpty() ? n : n + " (@" + u + ")") : u.isEmpty() ? null : "@" + u;
        }
        conversaService.receber(caixa, new ConversaService.Recebida(contato, new ContatoService.Origem(nome, null, null, null),
                conteudo, formato, anexos, idExterno == null || idExterno.isEmpty() ? null : idExterno, dados, eco, quando, false));
    }

    private AtendimentoVOs.Anexo baixar(String url, String tipo) throws Exception {
        String host = url.isEmpty() ? "" : Optional.ofNullable(URI.create(url).getHost()).orElse("").toLowerCase(Locale.ROOT);
        String hostApi = Optional.ofNullable(URI.create(props.getInstagramUrl()).getHost()).orElse("");
        boolean permitido = !host.isEmpty() && (host.equalsIgnoreCase(hostApi) || HOSTS_MIDIA.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h)));
        if (!permitido) {
            throw new Canal.FalhaDefinitiva("endereço de mídia inesperado");
        }
        ProvedorHttp.Arquivo a = http.baixar(url, null, props.getMidiaMaxima());
        return arquivos.salvar(a.dados(), null, a.tipo(), "file".equals(tipo) ? "arquivo" : tipo);
    }

    private static String link(String url) {
        return url == null || url.isEmpty() ? "" : " " + url;
    }

}
