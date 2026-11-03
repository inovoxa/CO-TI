package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.TelefoneVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.TelefoneDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.TelefoneMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.TELEFONE_NAO_EXISTE;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.TELEFONE_SEM_NUMERO;

/**
 * Lista de ramais e telefones, a mesma para todas as unidades: quem liga precisa achar o número de outra unidade.
 */
@Service
public class TelefoneService {

    @Resource
    private TelefoneMapper telefoneMapper;

    public PageResult<TelefoneVOs.Resp> pagina(TelefoneVOs.PaginaReq req) {
        return BeanUtils.toBean(telefoneMapper.pagina(req), TelefoneVOs.Resp.class);
    }

    public List<String> setores() {
        return telefoneMapper.setores();
    }

    public Long salvar(TelefoneVOs.SalvarReq req) {
        TelefoneDO t = TelefoneDO.builder().id(req.getId()).nome(req.getNome().trim())
                .setor(vazioNulo(req.getSetor())).unidade(vazioNulo(req.getUnidade()))
                .ramal(vazioNulo(req.getRamal())).telefone(vazioNulo(req.getTelefone()))
                .email(vazioNulo(req.getEmail())).observacao(vazioNulo(req.getObservacao())).build();
        if (t.getRamal() == null && t.getTelefone() == null) {
            throw exception(TELEFONE_SEM_NUMERO);
        }
        if (req.getId() == null) {
            telefoneMapper.insert(t);
        } else {
            obter(req.getId());
            telefoneMapper.updateById(t);
        }
        return t.getId();
    }

    public void excluir(Long id) {
        obter(id);
        telefoneMapper.deleteById(id);
    }

    private void obter(Long id) {
        if (telefoneMapper.selectById(id) == null) {
            throw exception(TELEFONE_NAO_EXISTE);
        }
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
