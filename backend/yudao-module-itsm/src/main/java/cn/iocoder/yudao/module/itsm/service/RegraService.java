package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CatalogoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.RegraDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.RegraMapper;
import cn.iocoder.yudao.module.itsm.dominio.MotorRegras;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Cadastro das regras de chamado e avaliação na abertura (GLPI: RuleTicketCollection).
 */
@Service
public class RegraService {

    @Resource
    private RegraMapper regraMapper;

    public List<CatalogoVOs.RegraResp> listar() {
        return regraMapper.selectList(new LambdaQueryWrapperX<RegraDO>().orderByAsc(RegraDO::getOrdem)
                .orderByAsc(RegraDO::getId)).stream().map(RegraService::resp).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(CatalogoVOs.RegraSalvar req) {
        List<String> erros = MotorRegras.validar(req.getCriterios(), req.getAcoes());
        if (!erros.isEmpty()) {
            throw exception(REGRA_INVALIDA, String.join("; ", erros));
        }
        RegraDO r = RegraDO.builder().id(req.getId()).nome(req.getNome().trim()).descricao(req.getDescricao())
                .ordem(req.getOrdem()).ativo(req.getAtivo()).combinacao(req.getCombinacao()).parar(req.getParar())
                .criterios(JsonUtils.toJsonString(req.getCriterios())).acoes(JsonUtils.toJsonString(req.getAcoes())).build();
        if (req.getId() == null) {
            regraMapper.insert(r);
        } else {
            obter(req.getId());
            regraMapper.updateById(r);
        }
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        obter(id);
        regraMapper.deleteById(id);
    }

    /** Regras ativas, na ordem de avaliação. */
    public List<MotorRegras.Regra> ativas() {
        return regraMapper.selectList(new LambdaQueryWrapperX<RegraDO>().eq(RegraDO::getAtivo, true)
                .orderByAsc(RegraDO::getOrdem).orderByAsc(RegraDO::getId)).stream()
                .map(r -> new MotorRegras.Regra(r.getId(), r.getNome(), !"OU".equals(r.getCombinacao()),
                        Boolean.TRUE.equals(r.getParar()), criterios(r.getCriterios()), acoes(r.getAcoes())))
                .toList();
    }

    public List<MotorRegras.Disparo> avaliar(Map<String, Object> fatos) {
        return MotorRegras.avaliar(ativas(), fatos);
    }

    private RegraDO obter(Long id) {
        RegraDO r = id == null ? null : regraMapper.selectById(id);
        if (r == null) {
            throw exception(REGRA_NAO_EXISTE);
        }
        return r;
    }

    private static CatalogoVOs.RegraResp resp(RegraDO r) {
        CatalogoVOs.RegraResp v = new CatalogoVOs.RegraResp();
        v.setId(r.getId());
        v.setNome(r.getNome());
        v.setDescricao(r.getDescricao());
        v.setOrdem(r.getOrdem());
        v.setAtivo(r.getAtivo());
        v.setCombinacao(r.getCombinacao());
        v.setParar(r.getParar());
        v.setCriterios(criterios(r.getCriterios()));
        v.setAcoes(acoes(r.getAcoes()));
        v.setUpdateTime(r.getUpdateTime());
        return v;
    }

    private static List<MotorRegras.Criterio> criterios(String json) {
        List<MotorRegras.Criterio> l = JsonUtils.parseObject(json, new TypeReference<List<MotorRegras.Criterio>>() {
        });
        return l == null ? List.of() : l;
    }

    private static List<MotorRegras.Acao> acoes(String json) {
        List<MotorRegras.Acao> l = JsonUtils.parseObject(json, new TypeReference<List<MotorRegras.Acao>>() {
        });
        return l == null ? List.of() : l;
    }

}
