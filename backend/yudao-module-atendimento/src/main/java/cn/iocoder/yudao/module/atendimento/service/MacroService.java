package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.MacroDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.MacroMapper;
import cn.iocoder.yudao.module.atendimento.dominio.Automacoes;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Macros: ações que o atendente executa numa conversa com um clique. Pessoal (só o dono) ou para todos (criar e
 * alterar exige {@code atendimento:macro:todos}).
 */
@Service
public class MacroService {

    public static final String P_TODOS = "atendimento:macro:todos";

    @Resource
    private MacroMapper macroMapper;
    @Resource
    private ValidacaoAcoes validacaoAcoes;
    @Resource
    private AcoesConversa acoesConversa;
    @Resource
    private ConversaService conversaService;
    @Resource
    private Acesso acesso;

    /** As da pessoa e as para todos. */
    public List<AtendimentoVOs.Macro> listar(Long userId) {
        return macroMapper.selectList(new LambdaQueryWrapperX<MacroDO>().and(w -> w.eq(MacroDO::getVisibilidade, "todos")
                        .or().eq(MacroDO::getDonoId, userId)).orderByAsc(MacroDO::getNome))
                .stream().map(m -> converter(m, userId)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(AtendimentoVOs.Macro req, Long userId) {
        boolean todos = "todos".equals(req.getVisibilidade());
        if (todos && !acesso.pode(userId, P_TODOS)) {
            throw exception(MACRO_SEM_PERMISSAO);
        }
        List<Automacoes.Acao> acs = validacaoAcoes.validar(req.getAcoes());
        MacroDO atual = req.getId() == null ? null : macroMapper.selectById(req.getId());
        if (req.getId() != null) {
            exigirEdicao(atual, userId);
        }
        MacroDO m = MacroDO.builder().id(req.getId()).nome(req.getNome().strip()).visibilidade(todos ? "todos" : "pessoal")
                // a macro pessoal é de quem cria; a "para todos" mantém o autor
                .donoId(atual != null ? atual.getDonoId() : userId).acoes(JsonUtils.toJsonString(acs)).build();
        if (atual == null) {
            macroMapper.insert(m);
        } else {
            macroMapper.updateById(m);
        }
        return m.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id, Long userId) {
        exigirEdicao(macroMapper.selectById(id), userId);
        macroMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> executar(Long macroId, Long conversaId, Long userId) {
        MacroDO m = macroMapper.selectById(macroId);
        if (m == null || !("todos".equals(m.getVisibilidade()) || userId.equals(m.getDonoId()))) {
            throw exception(MACRO_NAO_EXISTE);
        }
        ConversaDO c = conversaService.existente(conversaId);
        acesso.exigir(c, userId);
        List<String> feito = acoesConversa.executar(conversaId, AutomacaoService.acoes(m.getAcoes()), userId);
        conversaService.atividade(c, conversaService.nome(userId) + " executou a macro «" + m.getNome() + "»"
                + (feito.isEmpty() ? " (nada a mudar)" : ": " + String.join(", ", feito)));
        conversaService.publicar("conversa", c.getId(), null);
        return feito;
    }

    /** Pessoal: só o dono. Para todos: quem tem a permissão. */
    private void exigirEdicao(MacroDO m, Long userId) {
        if (m == null) {
            throw exception(MACRO_NAO_EXISTE);
        }
        boolean pode = "todos".equals(m.getVisibilidade()) ? acesso.pode(userId, P_TODOS) : userId.equals(m.getDonoId());
        if (!pode) {
            throw exception("todos".equals(m.getVisibilidade()) ? MACRO_SEM_PERMISSAO : MACRO_NAO_EXISTE);
        }
    }

    private AtendimentoVOs.Macro converter(MacroDO m, Long userId) {
        AtendimentoVOs.Macro v = new AtendimentoVOs.Macro();
        v.setId(m.getId());
        v.setNome(m.getNome());
        v.setVisibilidade(m.getVisibilidade());
        v.setMinha(userId.equals(m.getDonoId()));
        v.setAcoes(ValidacaoAcoes.paraVO(AutomacaoService.acoes(m.getAcoes())));
        return v;
    }

}
