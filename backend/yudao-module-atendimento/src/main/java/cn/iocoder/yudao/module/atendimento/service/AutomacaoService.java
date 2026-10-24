package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import cn.iocoder.yudao.module.atendimento.dominio.Automacoes;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Automações do atendimento (evento → condições → ações). Ver docs/specs/atendimento-automacoes.md.
 *
 * <p>Roda na mesma transação do evento. O que uma automação faz não dispara outras: enquanto as ações rodam, a
 * linha de execução fica marcada e novos eventos são ignorados.
 */
@Slf4j
@Service
public class AutomacaoService {

    private static final ThreadLocal<Boolean> EM_EXECUCAO = new ThreadLocal<>();

    @Resource
    private AutomacaoMapper automacaoMapper;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private ConversaEtiquetaMapper conversaEtiquetaMapper;
    @Resource
    private AcoesConversa acoesConversa;
    @Resource
    private ConversaService conversaService;
    @Resource
    private HorarioService horarioService;
    @Resource
    private ValidacaoAcoes validacaoAcoes;
    @Resource
    private AuditoriaService auditoria;

    /** Evento na conversa: roda as regras ativas desse evento cujas condições valem. */
    public void disparar(String evento, Long conversaId, String conteudo) {
        if (Boolean.TRUE.equals(EM_EXECUCAO.get())) {
            return;
        }
        List<AutomacaoDO> regras = automacaoMapper.selectList(new LambdaQueryWrapperX<AutomacaoDO>().eq(AutomacaoDO::getEvento, evento)
                .eq(AutomacaoDO::getAtiva, true).orderByAsc(AutomacaoDO::getOrdem).orderByAsc(AutomacaoDO::getId));
        if (regras.isEmpty()) {
            return;
        }
        EM_EXECUCAO.set(true);
        try {
            for (AutomacaoDO r : regras) {
                try {
                    ConversaDO c = conversaMapper.selectById(conversaId);
                    if (c == null || !Automacoes.atende(condicoes(r.getCondicoes()), contexto(c, conteudo))) {
                        continue;
                    }
                    List<String> feito = acoesConversa.executar(conversaId, acoes(r.getAcoes()), null);
                    automacaoMapper.registrarExecucao(r.getId());
                    if (!feito.isEmpty()) {
                        conversaService.atividade(c, "Automação «" + r.getNome() + "»: " + String.join(", ", feito));
                    }
                } catch (RuntimeException e) {
                    log.warn("[disparar][automação {} na conversa {}: {}]", r.getId(), conversaId, e.getMessage());
                }
            }
        } finally {
            EM_EXECUCAO.remove();
        }
    }

    private Automacoes.Contexto contexto(ConversaDO c, String conteudo) {
        CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
        ContatoDO contato = contatoMapper.selectById(c.getContatoId());
        Set<Long> etiquetas = conversaEtiquetaMapper.listar(List.of(c.getId())).stream()
                .map(x -> ((Number) x.get("etiqueta_id")).longValue()).collect(Collectors.toSet());
        return new Automacoes.Contexto(c.getCaixaId(), caixa == null ? null : caixa.getCanal(), c.getStatus(), c.getPrioridade(),
                c.getAgenteId(), etiquetas, conteudo, contato == null ? null : contato.getEmail(),
                contato == null ? null : contato.getTelefone(), contato != null && contato.getUserId() != null,
                () -> caixa == null || horarioService.emExpediente(caixa));
    }

    // =====================================================================================================
    // Cadastro
    // =====================================================================================================

    public List<AtendimentoVOs.Automacao> listar() {
        return automacaoMapper.selectList(new LambdaQueryWrapperX<AutomacaoDO>().orderByAsc(AutomacaoDO::getOrdem).orderByAsc(AutomacaoDO::getId))
                .stream().map(AutomacaoService::converter).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(AtendimentoVOs.Automacao req, Long userId) {
        if (!Automacoes.EVENTOS.contains(req.getEvento())) {
            throw exception(ACAO_INVALIDA, "evento desconhecido: " + req.getEvento());
        }
        List<Automacoes.Condicao> conds = req.getCondicoes().stream()
                .map(c -> new Automacoes.Condicao(c.getCampo(), c.getOperador(), c.getValor())).toList();
        for (Automacoes.Condicao c : conds) {
            String p = Automacoes.problemaCondicao(c);
            if (p != null) {
                throw exception(ACAO_INVALIDA, p);
            }
        }
        List<Automacoes.Acao> acs = validacaoAcoes.validar(req.getAcoes());
        AutomacaoDO d = AutomacaoDO.builder().id(req.getId()).nome(req.getNome().strip()).descricao(ContatoService.vazio(req.getDescricao()))
                .evento(req.getEvento()).condicoes(JsonUtils.toJsonString(conds)).acoes(JsonUtils.toJsonString(acs))
                .ativa(!Boolean.FALSE.equals(req.getAtiva())).ordem(req.getOrdem() == null ? 0 : req.getOrdem()).build();
        if (d.getId() == null) {
            d.setExecucoes(0L);
            automacaoMapper.insert(d);
        } else {
            if (automacaoMapper.selectById(d.getId()) == null) {
                throw exception(AUTOMACAO_NAO_EXISTE);
            }
            automacaoMapper.updateById(d);
            automacaoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AutomacaoDO>()
                    .eq(AutomacaoDO::getId, d.getId()).set(AutomacaoDO::getDescricao, d.getDescricao()));
        }
        auditoria.registrar("atendimento.automacao_salva", userId, null, Map.of("automacao", d.getId(), "nome", d.getNome(),
                "evento", d.getEvento(), "ativa", d.getAtiva()));
        return d.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id, Long userId) {
        AutomacaoDO d = automacaoMapper.selectById(id);
        if (d == null) {
            throw exception(AUTOMACAO_NAO_EXISTE);
        }
        automacaoMapper.deleteById(id);
        auditoria.registrar("atendimento.automacao_excluida", userId, null, Map.of("automacao", id, "nome", d.getNome()));
    }

    static List<Automacoes.Condicao> condicoes(String json) {
        List<Automacoes.Condicao> l = JsonUtils.parseObject(json, new TypeReference<List<Automacoes.Condicao>>() {
        });
        return l == null ? List.of() : l;
    }

    static List<Automacoes.Acao> acoes(String json) {
        List<Automacoes.Acao> l = JsonUtils.parseObject(json, new TypeReference<List<Automacoes.Acao>>() {
        });
        return l == null ? List.of() : l;
    }

    private static AtendimentoVOs.Automacao converter(AutomacaoDO d) {
        AtendimentoVOs.Automacao v = new AtendimentoVOs.Automacao();
        v.setId(d.getId());
        v.setNome(d.getNome());
        v.setDescricao(d.getDescricao());
        v.setEvento(d.getEvento());
        v.setAtiva(d.getAtiva());
        v.setOrdem(d.getOrdem());
        v.setExecucoes(d.getExecucoes());
        v.setUltimaExecucao(d.getUltimaExecucao());
        v.setCondicoes(condicoes(d.getCondicoes()).stream().map(c -> {
            AtendimentoVOs.Condicao x = new AtendimentoVOs.Condicao();
            x.setCampo(c.campo());
            x.setOperador(c.operador());
            x.setValor(c.valor());
            return x;
        }).collect(Collectors.toCollection(ArrayList::new)));
        v.setAcoes(ValidacaoAcoes.paraVO(acoes(d.getAcoes())));
        return v;
    }

}
