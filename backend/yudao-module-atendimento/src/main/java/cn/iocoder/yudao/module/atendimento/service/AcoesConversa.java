package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import cn.iocoder.yudao.module.atendimento.dominio.Automacoes;
import cn.iocoder.yudao.module.atendimento.enums.ConversaStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Executa as ações de macros e automações numa conversa. Ação que não se aplica (atendente que não atende a caixa,
 * etiqueta excluída) é pulada sem derrubar as outras.
 */
@Slf4j
@Service
public class AcoesConversa {

    @Resource
    private ConversaService conversaService;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private ConversaEtiquetaMapper conversaEtiquetaMapper;
    @Resource
    private EtiquetaMapper etiquetaMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private AtribuicaoService atribuicaoService;
    @Resource
    private Acesso acesso;

    /**
     * @param userId quem executa (macro) ou null (automação)
     * @return o que foi feito, em frases curtas ("atribuiu a Ana", "etiqueta Urgente")
     */
    public List<String> executar(Long conversaId, List<Automacoes.Acao> acoes, Long userId) {
        List<String> feito = new ArrayList<>();
        for (Automacoes.Acao a : acoes) {
            try {
                ConversaDO c = conversaMapper.selectById(conversaId);
                String d = aplicar(c, a, userId);
                if (d != null) {
                    feito.add(d);
                }
            } catch (RuntimeException e) {
                log.warn("[executar][conversa {} ação {}: {}]", conversaId, a.tipo(), e.getMessage());
            }
        }
        return feito;
    }

    private String aplicar(ConversaDO c, Automacoes.Acao a, Long userId) {
        switch (a.tipo()) {
            case "atribuir_agente" -> {
                Long ag = Automacoes.id(a.valor());
                if (ag == null || ag.equals(c.getAgenteId()) || !acesso.membro(c.getCaixaId(), ag)) {
                    return null;
                }
                conversaService.definirAgente(c, ag, null);
                return "atribuiu a " + conversaService.nome(ag);
            }
            case "remover_agente" -> {
                if (c.getAgenteId() == null) {
                    return null;
                }
                conversaService.definirAgente(c, null, null);
                return "tirou o atendente";
            }
            case "atribuir_automatico" -> {
                if (c.getAgenteId() != null) {
                    return null;
                }
                CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
                return atribuicaoService.distribuir(c, caixa).map(u -> {
                    conversaService.publicar("conversa", c.getId(), null);
                    return "distribuiu para " + conversaService.nome(u);
                }).orElse(null);
            }
            case "adicionar_etiquetas", "remover_etiquetas" -> {
                boolean incluir = "adicionar_etiquetas".equals(a.tipo());
                Set<Long> atuais = conversaEtiquetaMapper.listar(List.of(c.getId())).stream()
                        .map(r -> ((Number) r.get("etiqueta_id")).longValue()).collect(Collectors.toSet());
                List<Long> alvo = Automacoes.ids(a.valor()).stream().filter(e -> incluir != atuais.contains(e)).toList();
                List<EtiquetaDO> etiquetas = alvo.isEmpty() ? List.of() : etiquetaMapper.selectBatchIds(alvo);
                if (etiquetas.isEmpty()) {
                    return null;
                }
                etiquetas.forEach(e -> {
                    if (incluir) {
                        conversaEtiquetaMapper.incluir(c.getId(), e.getId());
                    } else {
                        conversaEtiquetaMapper.remover(c.getId(), e.getId());
                    }
                });
                conversaService.publicar("conversa", c.getId(), null);
                String nomes = etiquetas.stream().map(EtiquetaDO::getNome).collect(Collectors.joining(", "));
                return (incluir ? "etiqueta " : "tirou a etiqueta ") + nomes;
            }
            case "enviar_mensagem" -> {
                conversaService.saida(c, texto(c, a.valor()), userId == null ? "sistema" : "agente", userId, List.of());
                return "mandou mensagem";
            }
            case "nota" -> {
                conversaService.nota(c, texto(c, a.valor()), userId);
                return "escreveu nota";
            }
            case "status" -> {
                ConversaStatus novo = switch (String.valueOf(a.valor())) {
                    case "resolvida" -> ConversaStatus.RESOLVIDA;
                    case "pendente" -> ConversaStatus.PENDENTE;
                    default -> ConversaStatus.ABERTA;
                };
                if (!conversaService.aplicarStatus(conversaMapper.selectByIdParaAtualizar(c.getId()), novo, null, null)) {
                    return null;
                }
                return switch (novo) {
                    case RESOLVIDA -> "resolveu";
                    case PENDENTE -> "deixou pendente";
                    default -> "reabriu";
                };
            }
            case "prioridade" -> {
                Long p = Automacoes.id(a.valor());
                Integer nova = p == null || p == 0 ? null : p.intValue();
                if (Objects.equals(nova, c.getPrioridade())) {
                    return null;
                }
                conversaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConversaDO>()
                        .eq(ConversaDO::getId, c.getId()).set(ConversaDO::getPrioridade, nova));
                conversaService.publicar("conversa", c.getId(), null);
                return nova == null ? "tirou a prioridade" : "prioridade " + ROTULO_PRIORIDADE.get(nova);
            }
            default -> {
                return null;
            }
        }
    }

    private static final Map<Integer, String> ROTULO_PRIORIDADE = Map.of(1, "baixa", 2, "média", 3, "alta", 4, "urgente");

    private String texto(ConversaDO c, Object valor) {
        ContatoDO contato = contatoMapper.selectById(c.getContatoId());
        Map<String, String> v = new HashMap<>();
        v.put("contato.nome", contato == null ? "" : contato.getNome());
        v.put("conversa.id", String.valueOf(c.getId()));
        v.put("atendente.nome", c.getAgenteId() == null ? "" : conversaService.nome(c.getAgenteId()));
        return Automacoes.preencher(String.valueOf(valor), v);
    }

}
