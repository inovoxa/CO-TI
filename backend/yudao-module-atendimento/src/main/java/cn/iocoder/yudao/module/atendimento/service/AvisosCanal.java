package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import cn.iocoder.yudao.module.atendimento.dominio.Telefone;
import cn.iocoder.yudao.module.atendimento.enums.ConversaStatus;
import cn.iocoder.yudao.module.itsm.dal.dataobject.AprovacaoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoAtorDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.InteracaoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.AprovacaoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoAtorMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.InteracaoMapper;
import cn.iocoder.yudao.module.itsm.dominio.Aprovacoes;
import cn.iocoder.yudao.module.itsm.service.ChamadoEvento;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Avisos de volta pelo canal (no lugar dos workflows 13, 14 e 20 do n8n): andamento, solução e decisão de aprovação
 * do chamado aberto pela conversa vão ao contato; o pedido de aprovação vai ao aprovador, pelo celular do cadastro.
 * Fora da janela de 24 h do WhatsApp oficial, o aviso usa o modelo aprovado da caixa; sem modelo, não sai e fica
 * registrado na conversa. Roda depois do commit, numa fila própria: o aviso nunca atrasa nem desfaz o chamado.
 */
@Slf4j
@Component
public class AvisosCanal {

    private static final Set<String> ACOES = Set.of("acompanhamento", "solucionado", "solucao", "aprovado", "recusado", "fechado",
            "aprovacao-solicitada");

    /** O fluxo do WhatsApp liga o chamado à conversa logo depois de o abrir: o aviso espera um pouco por isso */
    private static final int TENTATIVAS = 4;
    private static final int ESPERA_SEG = 2;

    private final ScheduledExecutorService fila = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "atendimento-avisos");
        t.setDaemon(true);
        return t;
    });

    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private ModeloMapper modeloMapper;
    @Resource
    private ContatoService contatoService;
    @Resource
    private ConversaService conversaService;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoAtorMapper chamadoAtorMapper;
    @Resource
    private InteracaoMapper interacaoMapper;
    @Resource
    private AprovacaoMapper aprovacaoMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private AtendimentoProperties props;
    @Resource
    private TransactionTemplate transactionTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoMudarChamado(ChamadoEvento e) {
        if (!ACOES.contains(e.acao())) {
            return;
        }
        agendar(e, 1, 0);
    }

    private void agendar(ChamadoEvento e, int tentativa, int atrasoSeg) {
        fila.schedule(() -> {
            try {
                Boolean semConversa = transactionTemplate.execute(s -> tratar(e));
                // chamado recém-aberto pelo fluxo: a conversa ainda pode não estar ligada a ele
                if (Boolean.TRUE.equals(semConversa) && tentativa < TENTATIVAS && Set.of("aprovacao-solicitada", "criado").contains(e.acao())) {
                    agendar(e, tentativa + 1, ESPERA_SEG);
                }
            } catch (Exception ex) {
                log.error("[aoMudarChamado][chamado {} ação {}]", e.chamadoId(), e.acao(), ex);
            }
        }, atrasoSeg, TimeUnit.SECONDS);
    }

    /** @return true se o chamado não está ligado a nenhuma conversa */
    boolean tratar(ChamadoEvento e) {
        ChamadoDO ch = chamadoMapper.selectById(e.chamadoId());
        if (ch == null) {
            return false;
        }
        // a conversa que abriu o chamado (a mais recente ligada a ele)
        ConversaDO c = conversaMapper.selectOne(new LambdaQueryWrapperX<ConversaDO>().eq(ConversaDO::getChamadoId, ch.getId())
                .orderByDesc(ConversaDO::getId).last("LIMIT 1"));
        if (c == null) {
            return true;
        }
        CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
        if (caixa == null || !Boolean.TRUE.equals(caixa.getAtivo()) || !Boolean.TRUE.equals(caixa.getAvisosAtivo())) {
            return false;
        }
        if ("aprovacao-solicitada".equals(e.acao())) {
            avisarAprovadores(ch, caixa);
            return false;
        }
        ContatoDO contato = contatoMapper.selectById(c.getContatoId());
        String texto = switch (e.acao()) {
            case "acompanhamento" -> {
                InteracaoDO i = e.refId() == null ? null : interacaoMapper.selectById(e.refId());
                if (i == null || Boolean.TRUE.equals(i.getPrivado())
                        || (i.getUserId() != null && i.getUserId().equals(contato.getUserId()))) {
                    yield null;   // nota interna, ou o próprio contato escreveu pelo portal
                }
                yield "📝 Chamado #" + ch.getId() + " — atualização da equipe:\n" + texto(i.getConteudo());
            }
            case "solucionado", "solucao" -> {
                InteracaoDO i = e.refId() == null ? null : interacaoMapper.selectById(e.refId());
                yield "✅ Chamado #" + ch.getId() + " solucionado" + (i == null ? "." : ":\n" + texto(i.getConteudo()))
                        + "\n\nSe não resolveu, responda aqui ou recuse a solução no portal.";
            }
            case "aprovado" -> ch.getValidacao() != null && ch.getValidacao() == Aprovacoes.APROVADA
                    ? "👍 Chamado #" + ch.getId() + ": o pedido foi aprovado e segue para atendimento." : null;
            case "recusado" -> "Chamado #" + ch.getId() + ": o pedido foi recusado pelo aprovador.";
            case "fechado" -> "Chamado #" + ch.getId() + " encerrado. Obrigado!";
            default -> null;
        };
        if (texto != null) {
            enviar(c, caixa, ch, texto);
        }
        return false;
    }

    /** Pedido de aprovação: avisa cada aprovador pendente pelo celular do cadastro, na mesma caixa. */
    private void avisarAprovadores(ChamadoDO ch, CaixaDO caixa) {
        if (!"whatsapp_cloud".equals(caixa.getCanal()) && !"api".equals(caixa.getCanal())) {
            return;   // Telegram e Instagram não têm como chegar a alguém pelo número
        }
        String solicitante = chamadoAtorMapper.selectList(new LambdaQueryWrapperX<ChamadoAtorDO>().eq(ChamadoAtorDO::getChamadoId, ch.getId())
                        .eq(ChamadoAtorDO::getTipo, 1)).stream().map(a -> a.getUserId() != null
                        ? Optional.ofNullable(adminUserApi.getUser(a.getUserId())).map(AdminUserRespDTO::getNickname).orElse(null) : a.getEmail())
                .filter(Objects::nonNull).findFirst().orElse("um colaborador");
        for (AprovacaoDO a : aprovacaoMapper.selectList(new LambdaQueryWrapperX<AprovacaoDO>().eq(AprovacaoDO::getChamadoId, ch.getId())
                .eq(AprovacaoDO::getStatus, Aprovacoes.AGUARDANDO))) {
            AdminUserRespDTO u = adminUserApi.getUser(a.getAprovadorId());
            String tel = u == null ? null : Telefone.normalizar(u.getMobile());
            if (tel == null) {
                continue;
            }
            String origem = "whatsapp_cloud".equals(caixa.getCanal()) ? tel.substring(1) : tel;
            ContatoCanalDO cc = contatoService.encontrarOuCriar(caixa.getId(), origem,
                    new ContatoService.Origem(u.getNickname(), tel, u.getEmail(), null));
            ConversaDO c = conversaMapper.selectOne(new LambdaQueryWrapperX<ConversaDO>().eq(ConversaDO::getContatoCanalId, cc.getId())
                    .orderByDesc(ConversaDO::getId).last("LIMIT 1"));
            if (c == null) {
                // conversa só de avisos: nasce resolvida; se o aprovador responder, reabre como qualquer outra
                c = ConversaDO.builder().caixaId(caixa.getId()).contatoId(cc.getContatoId()).contatoCanalId(cc.getId())
                        .status(ConversaStatus.RESOLVIDA.getCodigo()).resolvidaEm(LocalDateTime.now()).ultimaAtividadeEm(LocalDateTime.now())
                        .atributos("{}").build();
                conversaMapper.insert(c);
            }
            String base = props.getUrlPublica() == null ? "" : props.getUrlPublica().replaceAll("/+$", "");
            enviar(c, caixa, ch, "🔔 Olá, " + u.getNickname() + "! O pedido *#" + ch.getId() + " — " + ch.getTitulo() + "*, de " + solicitante
                    + ", aguarda a sua aprovação.\n\nAprove ou recuse pelo portal: " + base + "/aprovacoes");
        }
    }

    /** Manda o aviso; fora da janela de 24 h, pelo modelo da caixa (ou não manda e registra). */
    private void enviar(ConversaDO c, CaixaDO caixa, ChamadoDO ch, String texto) {
        boolean janelaFechada = ConversaService.JANELA_24H.contains(caixa.getCanal())
                && (c.getUltimaEntradaEm() == null || c.getUltimaEntradaEm().isBefore(LocalDateTime.now().minusHours(24)));
        if (!janelaFechada) {
            conversaService.saida(c, texto, "sistema", null, List.of(), Map.of("aviso", ch.getId()));
            conversaService.publicar("conversa", c.getId(), null);
            return;
        }
        ModeloDO m = caixa.getAvisoModeloId() == null ? null : modeloMapper.selectById(caixa.getAvisoModeloId());
        if (m == null || !"APPROVED".equals(m.getStatus()) || m.getVariaveis() == null || m.getVariaveis() != 1) {
            conversaService.atividade(c, "Aviso do chamado #" + ch.getId() + " não enviado: passaram 24 h sem mensagem do contato"
                    + " e a caixa não tem um modelo de aviso aprovado (com uma variável).");
            return;
        }
        // o WhatsApp não aceita quebra de linha nem tabulação nos parâmetros do modelo
        String parametro = texto.replaceAll("\\s*\\n+\\s*", " · ").replaceAll("\\s{4,}", " ");
        parametro = parametro.length() > 1000 ? parametro.substring(0, 997) + "..." : parametro;
        List<String> nomes = ModeloService.variaveis(m.getCorpo());
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nome", m.getNome());
        modelo.put("idioma", m.getIdioma());
        modelo.put("parametros", List.of(parametro));
        if (nomes.stream().anyMatch(n -> !n.matches("\\d+"))) {
            modelo.put("nomesVariaveis", nomes);
        }
        conversaService.saida(c, ModeloService.preencher(m.getCorpo(), nomes, List.of(parametro)), "sistema", null, List.of(),
                Map.of("modelo", modelo, "aviso", ch.getId()));
        conversaService.publicar("conversa", c.getId(), null);
    }

    /** HTML do acompanhamento em texto para o canal. */
    static String texto(String html) {
        if (html == null) {
            return "";
        }
        String t = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</(p|div|li|h\\d)>", "\n").replaceAll("<[^>]+>", "");
        t = HtmlUtils.htmlUnescape(t).replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").strip();
        return t.length() > 1500 ? t.substring(0, 1497) + "..." : t;
    }

    @PreDestroy
    public void parar() {
        fila.shutdownNow();
    }

}
