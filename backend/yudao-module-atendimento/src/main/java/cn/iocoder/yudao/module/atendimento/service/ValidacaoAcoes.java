package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.mysql.EtiquetaMapper;
import cn.iocoder.yudao.module.atendimento.dominio.Automacoes;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.ACAO_INVALIDA;

/**
 * Confere as ações de macros e automações antes de gravar: tipo conhecido, valor coerente, atendente e etiquetas que
 * existem.
 */
@Component
public class ValidacaoAcoes {

    @Resource
    private EtiquetaMapper etiquetaMapper;
    @Resource
    private AdminUserApi adminUserApi;

    public List<Automacoes.Acao> validar(List<AtendimentoVOs.AcaoVO> lista) {
        if (lista == null || lista.isEmpty()) {
            throw exception(ACAO_INVALIDA, "inclua ao menos uma ação");
        }
        List<Automacoes.Acao> out = new ArrayList<>();
        for (AtendimentoVOs.AcaoVO a : lista) {
            Automacoes.Acao x = new Automacoes.Acao(a.getTipo(), a.getValor());
            String p = Automacoes.problemaAcao(x);
            if (p != null) {
                throw exception(ACAO_INVALIDA, p);
            }
            if ("atribuir_agente".equals(x.tipo())) {
                adminUserApi.validateUserList(List.of(Automacoes.id(x.valor())));
            }
            if (x.tipo().endsWith("_etiquetas")) {
                Set<Long> ids = new HashSet<>(Automacoes.ids(x.valor()));
                if (etiquetaMapper.selectBatchIds(ids).size() != ids.size()) {
                    throw exception(ACAO_INVALIDA, "etiqueta não encontrada");
                }
                x = new Automacoes.Acao(x.tipo(), new ArrayList<>(ids));
            }
            out.add(x);
        }
        return out;
    }

    static List<AtendimentoVOs.AcaoVO> paraVO(List<Automacoes.Acao> acoes) {
        List<AtendimentoVOs.AcaoVO> out = new ArrayList<>();
        for (Automacoes.Acao a : acoes) {
            AtendimentoVOs.AcaoVO v = new AtendimentoVOs.AcaoVO();
            v.setTipo(a.tipo());
            v.setValor(a.valor());
            out.add(v);
        }
        return out;
    }

}
