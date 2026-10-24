package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import cn.iocoder.yudao.module.atendimento.dominio.PromptsIa;
import cn.iocoder.yudao.module.atendimento.enums.ConversaStatus;
import cn.iocoder.yudao.module.atendimento.enums.MensagemTipo;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Assistente de IA nas caixas (docs/specs/atendimento-ia.md). A conversa nova numa caixa com assistente fica pendente
 * com ele; cada mensagem do contato é respondida fora da requisição, numa fila de uma linha só (mensagens seguidas
 * são respondidas de uma vez). Transborda para humano quando o modelo pede, no limite de respostas ou com o provedor
 * falhando.
 */
@Slf4j
@Service
public class AssistenteService {

    private static final int MAX_RODADAS = 4;
    private static final int HISTORICO = 20;

    private final ScheduledExecutorService fila = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "atendimento-assistente");
        t.setDaemon(true);
        return t;
    });
    private final Set<Long> agendadas = ConcurrentHashMap.newKeySet();

    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ConversaService conversaService;
    @Resource
    private AtribuicaoService atribuicaoService;
    @Resource
    private IaService iaService;
    @Resource
    private FerramentasAssistente ferramentas;
    @Resource
    private FluxoService fluxoService;
    @Resource
    private TransactionTemplate transactionTemplate;

    /** A caixa tem o fluxo do catálogo, ou assistente com a IA ligada: a conversa nova fica com o robô. */
    public boolean ativoPara(CaixaDO caixa) {
        return caixa != null && (Boolean.TRUE.equals(caixa.getFluxoAtivo()) || (Boolean.TRUE.equals(caixa.getRoboAtivo()) && iaService.ativo()));
    }

    /** Chamado na transação que gravou a mensagem do contato: responde depois que ela for confirmada. */
    void agendarAposGravar(Long conversaId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agendar(conversaId, 0);
                }
            });
        } else {
            agendar(conversaId, 0);
        }
    }

    private void agendar(Long conversaId, long atrasoSeg) {
        if (!agendadas.add(conversaId)) {
            return;   // já na fila: a resposta vai considerar também esta mensagem
        }
        fila.schedule(() -> {
            agendadas.remove(conversaId);
            try {
                responder(conversaId);
            } catch (Exception e) {
                log.error("[agendar][conversa {}]", conversaId, e);
            }
        }, atrasoSeg, TimeUnit.SECONDS);
    }

    void responder(Long conversaId) {
        ConversaDO c = conversaMapper.selectById(conversaId);
        if (c == null || !Boolean.TRUE.equals(c.getRobo()) || c.getAgenteId() != null || c.getStatus() != ConversaStatus.PENDENTE.getCodigo()) {
            return;
        }
        CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
        if (caixa != null && Boolean.TRUE.equals(caixa.getFluxoAtivo())) {
            fluxoService.processar(conversaId);   // o fluxo do catálogo atende primeiro
            return;
        }
        if (!ativoPara(caixa)) {
            transferir(conversaId, "o assistente foi desligado");
            return;
        }
        int limite = caixa.getRoboMaxRespostas() == null ? 10 : caixa.getRoboMaxRespostas();
        if (c.getRoboRespostas() != null && c.getRoboRespostas() >= limite) {
            transferir(conversaId, "limite de " + limite + " respostas do assistente");
            return;
        }
        List<MensagemDO> hist = new ArrayList<>(mensagemMapper.selectList(new LambdaQueryWrapperX<MensagemDO>()
                .eq(MensagemDO::getConversaId, conversaId).eq(MensagemDO::getPrivada, false)
                .in(MensagemDO::getTipo, MensagemTipo.ENTRADA, MensagemTipo.SAIDA).orderByDesc(MensagemDO::getId).last("LIMIT " + HISTORICO)));
        Collections.reverse(hist);
        if (hist.isEmpty() || hist.get(hist.size() - 1).getTipo() != MensagemTipo.ENTRADA) {
            return;   // nada novo do contato para responder
        }
        StringBuilder pergunta = new StringBuilder();
        for (int i = hist.size() - 1; i >= 0 && hist.get(i).getTipo() == MensagemTipo.ENTRADA; i--) {
            pergunta.insert(0, hist.get(i).getConteudo() + " ");
        }
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(PromptsIa.mensagem("system", PromptsIa.sistemaAssistente(caixa.getRoboNome(), caixa.getRoboInstrucoes(),
                ferramentas.artigos(pergunta.toString(), true, 3))));
        msgs.addAll(PromptsIa.historico(hist.stream().map(m -> new PromptsIa.Fala(m.getTipo() == MensagemTipo.ENTRADA, falar(m))).toList()));
        String resposta = null;
        try {
            for (int rodada = 0; rodada < MAX_RODADAS; rodada++) {
                IaService.Resposta r = iaService.conversar(msgs, PromptsIa.ferramentas(), "assistente", conversaId, null);
                if (r.chamadas().isEmpty()) {
                    resposta = r.conteudo();
                    break;
                }
                Map<String, Object> pedido = new LinkedHashMap<>(r.mensagemBruta());
                pedido.putIfAbsent("content", null);
                msgs.add(pedido);
                for (IaService.Chamada ch : r.chamadas()) {
                    if ("transferir_para_humano".equals(ch.nome())) {
                        String motivo = String.valueOf(ch.argumentos().getOrDefault("motivo", "o assistente pediu ajuda")).strip();
                        enviar(conversaId, PromptsIa.MSG_TRANSFERENCIA, false);
                        transferir(conversaId, motivo.isEmpty() ? "o assistente pediu ajuda" : ContatoService.cortar(motivo, 300));
                        return;
                    }
                    String resultado = transactionTemplate.execute(s -> ferramentas.executar(conversaMapper.selectById(conversaId), ch));
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("role", "tool");
                    m.put("tool_call_id", ch.id());
                    m.put("content", resultado);
                    msgs.add(m);
                }
            }
        } catch (IllegalStateException e) {
            log.warn("[responder][conversa {}: {}]", conversaId, e.getMessage());
            int falhas = conversaMapper.somarFalhaRobo(conversaId);
            if (falhas >= 2) {
                transferir(conversaId, "o provedor de IA não respondeu");
            } else {
                agendar(conversaId, 5);
            }
            return;
        }
        String texto = PromptsIa.paraCanal(resposta);
        if (texto.isEmpty()) {
            transferir(conversaId, "o assistente não soube responder");
            return;
        }
        enviar(conversaId, texto, true);
    }

    /** O que o modelo vê de cada mensagem: o texto e o nome dos anexos. */
    private static String falar(MensagemDO m) {
        String t = m.getConteudo() == null ? "" : m.getConteudo();
        if (m.getAnexos() != null && !"[]".equals(m.getAnexos())) {
            List<Map<String, Object>> anexos = JsonUtils.parseObject(m.getAnexos(), new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
            });
            for (Map<String, Object> a : anexos == null ? List.<Map<String, Object>>of() : anexos) {
                t = t + " [anexo: " + a.getOrDefault("nome", "arquivo") + "]";
            }
        }
        return t;
    }

    private void enviar(Long conversaId, String texto, boolean conta) {
        transactionTemplate.executeWithoutResult(s -> {
            ConversaDO c = conversaMapper.selectByIdParaAtualizar(conversaId);
            if (c == null || !Boolean.TRUE.equals(c.getRobo())) {
                return;   // alguém assumiu enquanto o modelo pensava
            }
            conversaService.saida(c, texto, "robo", null, List.of());
            if (conta) {
                conversaMapper.contarRespostaRobo(conversaId);
            }
            conversaService.publicar("conversa", conversaId, null);
        });
    }

    /** Passa para humano: aberta, sem o assistente, distribuída como uma conversa nova. */
    void transferir(Long conversaId, String motivo) {
        transactionTemplate.executeWithoutResult(s -> {
            ConversaDO c = conversaMapper.selectByIdParaAtualizar(conversaId);
            if (c == null || !Boolean.TRUE.equals(c.getRobo())) {
                return;
            }
            c.setRobo(false);
            if (!conversaService.aplicarStatus(c, ConversaStatus.ABERTA, null, "Assistente transferiu para atendimento humano: " + motivo)) {
                conversaMapper.updateById(ConversaDO.builder().id(c.getId()).robo(false).build());
            }
            CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
            if (c.getAgenteId() == null && caixa != null && Boolean.TRUE.equals(caixa.getAtribuicaoAutomatica())) {
                atribuicaoService.distribuir(c, caixa).ifPresent(a -> conversaService.atividade(c, "Atribuída automaticamente a " + conversaService.nome(a)));
            }
            conversaService.publicar("conversa", conversaId, null);
        });
    }

    @PreDestroy
    public void parar() {
        fila.shutdownNow();
    }

}
