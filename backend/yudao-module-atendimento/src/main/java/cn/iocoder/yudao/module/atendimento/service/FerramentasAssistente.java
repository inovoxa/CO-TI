package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ContatoDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.CaixaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ContatoMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ConversaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.IaMapper;
import cn.iocoder.yudao.module.atendimento.dominio.PromptsIa;
import cn.iocoder.yudao.module.atendimento.dominio.Relevancia;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoAtorDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoAtorMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dominio.BuscaTexto;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

/**
 * Ferramentas do assistente de IA. Agem só sobre o contato da conversa: nenhum argumento do modelo escolhe outra
 * pessoa, chamado ou registro (docs/specs/atendimento-ia.md).
 */
@Slf4j
@Component
public class FerramentasAssistente {

    @Resource
    private IaMapper iaMapper;
    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private ChamadoAtorMapper chamadoAtorMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private ConversaService conversaService;

    /**
     * Artigos publicados ligados ao texto.
     *
     * @param soFaq só os de FAQ (o que o assistente usa com o contato); o copiloto do atendente usa todos
     */
    public List<PromptsIa.Artigo> artigos(String texto, boolean soFaq, int limite) {
        String q = BuscaTexto.consultaQualquer(texto);
        if (q == null) {
            return List.of();
        }
        // candidatos pela busca de texto; a ordem final dá peso aos termos raros da pergunta (Relevancia)
        List<Relevancia.Candidato<PromptsIa.Artigo>> candidatos = iaMapper.artigos(q, soFaq, 30, 4000).stream().map(r -> {
            String titulo = String.valueOf(r.get("titulo"));
            String corpo = String.valueOf(r.get("texto"));
            PromptsIa.Artigo a = new PromptsIa.Artigo(((Number) r.get("id")).longValue(), titulo,
                    corpo.length() > 1500 ? corpo.substring(0, 1500) : corpo);
            return new Relevancia.Candidato<>(a, titulo, corpo);
        }).toList();
        return Relevancia.ordenar(texto, candidatos, limite);
    }

    /** Executa a ferramenta pedida e devolve o resultado em JSON (o modelo lê). */
    String executar(ConversaDO c, IaService.Chamada ch) {
        try {
            Map<String, Object> r = switch (ch.nome()) {
                case "buscar_base_conhecimento" -> {
                    List<PromptsIa.Artigo> a = artigos(String.valueOf(ch.argumentos().getOrDefault("consulta", "")), true, 3);
                    yield Map.of("artigos", a.stream().map(x -> Map.of("titulo", x.titulo(), "texto", x.texto())).toList());
                }
                case "consultar_chamados" -> chamados(c);
                case "abrir_chamado" -> abrirChamado(c, String.valueOf(ch.argumentos().getOrDefault("titulo", "")),
                        String.valueOf(ch.argumentos().getOrDefault("descricao", "")));
                default -> Map.of("erro", "ferramenta desconhecida: " + ch.nome());
            };
            return JsonUtils.toJsonString(r);
        } catch (RuntimeException e) {
            log.warn("[executar][conversa {} ferramenta {}: {}]", c.getId(), ch.nome(), e.getMessage());
            return JsonUtils.toJsonString(Map.of("erro", "não foi possível agora; ofereça transferir para uma pessoa"));
        }
    }

    private Map<String, Object> chamados(ConversaDO c) {
        ContatoDO contato = contatoMapper.selectById(c.getContatoId());
        Set<Long> ids = new LinkedHashSet<>();
        if (contato.getUserId() != null) {
            chamadoAtorMapper.selectList(new LambdaQueryWrapperX<ChamadoAtorDO>().eq(ChamadoAtorDO::getUserId, contato.getUserId())
                    .eq(ChamadoAtorDO::getTipo, 1)).forEach(a -> ids.add(a.getChamadoId()));
        }
        if (contato.getEmail() != null) {
            chamadoAtorMapper.selectList(new LambdaQueryWrapperX<ChamadoAtorDO>().apply("lower(email) = {0}", contato.getEmail())
                    .eq(ChamadoAtorDO::getTipo, 1)).forEach(a -> ids.add(a.getChamadoId()));
        }
        if (ids.isEmpty()) {
            return Map.of("chamados", List.of(), "observacao", contato.getUserId() == null && contato.getEmail() == null
                    ? "o contato não está identificado (sem e-mail nem conta no portal)" : "nenhum chamado encontrado");
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        for (ChamadoDO ch : chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>().in(ChamadoDO::getId, ids)
                .orderByDesc(ChamadoDO::getId).last("LIMIT 5"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", ch.getId());
            m.put("titulo", ch.getTitulo());
            m.put("situacao", ChamadoStatus.de(ch.getStatus()).getRotulo());
            m.put("aberto_em", ch.getAbertoEm() == null ? null : ch.getAbertoEm().toLocalDate().toString());
            lista.add(m);
        }
        return Map.of("chamados", lista);
    }

    private Map<String, Object> abrirChamado(ConversaDO c, String titulo, String descricao) {
        if (c.getChamadoId() != null) {
            return Map.of("numero", c.getChamadoId(), "observacao", "esta conversa já tem este chamado; não abra outro");
        }
        ContatoDO contato = contatoMapper.selectById(c.getContatoId());
        if (contato.getUserId() == null && contato.getEmail() == null) {
            return Map.of("erro", "o contato não está identificado; transfira para uma pessoa da equipe");
        }
        String t = titulo == null || titulo.isBlank() ? "Pedido pelo atendimento" : ContatoService.cortar(titulo.strip(), 120);
        CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
        ChamadoVOs.CriarReq req = new ChamadoVOs.CriarReq();
        req.setTitulo(t);
        req.setDescricao("<p>Aberto pelo assistente virtual na conversa #" + c.getId() + " (" + HtmlUtils.htmlEscape(caixa.getNome())
                + ") com <b>" + HtmlUtils.htmlEscape(contato.getNome()) + "</b>.</p><p>"
                + HtmlUtils.htmlEscape(ContatoService.cortar(descricao == null ? "" : descricao, 4000)) + "</p>");
        req.setUrgencia(3);
        req.setOrigemId(caixa.getCanal().startsWith("whatsapp") ? ConversaService.ORIGEM_WHATSAPP : ConversaService.ORIGEM_OUTRO);
        if (contato.getUserId() != null) {
            req.setSolicitanteIds(List.of(contato.getUserId()));
        }
        Long id = chamadoService.criarDeConversa(req, null, contato.getUserId() == null ? contato.getEmail() : null);
        conversaMapper.updateById(ConversaDO.builder().id(c.getId()).chamadoId(id).build());
        c.setChamadoId(id);
        conversaService.atividade(c, "Assistente abriu o chamado #" + id);
        return Map.of("numero", id, "situacao", "aberto");
    }

}
