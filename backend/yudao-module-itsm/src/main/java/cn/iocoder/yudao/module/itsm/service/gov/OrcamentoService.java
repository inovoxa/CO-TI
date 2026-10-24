package cn.iocoder.yudao.module.itsm.service.gov;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.OrcamentoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ProjetoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.OrcamentoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ProjetoMapper;
import cn.iocoder.yudao.module.itsm.service.ItilApoio;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.itsm.service.gov.CustoService.vazioNulo;

/**
 * Orçamentos (GLPI: Budget). O gasto é a soma dos custos de contratos, projetos e licenças ligados a ele.
 */
@Service
public class OrcamentoService {

    @Resource
    private OrcamentoMapper orcamentoMapper;
    @Resource
    private ProjetoMapper projetoMapper;
    @Resource
    private CustoService custoService;
    @Resource
    private ItilApoio apoio;

    public PageResult<GovernancaVOs.Orcamento> pagina(GovernancaVOs.Filtro f) {
        LambdaQueryWrapperX<OrcamentoDO> q = new LambdaQueryWrapperX<OrcamentoDO>().eqIfPresent(OrcamentoDO::getDeptId, f.getDeptId());
        if (f.getBusca() != null && !f.getBusca().isBlank()) {
            String like = "%" + f.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("nome ILIKE {0}", like).or().apply("tipo ILIKE {0}", like));
        }
        q.orderByDesc(OrcamentoDO::getFim).orderByAsc(OrcamentoDO::getNome);
        PageResult<OrcamentoDO> p = orcamentoMapper.selectPage(f, q);
        return new PageResult<>(converter(p.getList(), false), p.getTotal());
    }

    public List<GovernancaVOs.Resumo> simples() {
        return orcamentoMapper.selectList(new LambdaQueryWrapperX<OrcamentoDO>().orderByDesc(OrcamentoDO::getFim)).stream().map(o -> {
            GovernancaVOs.Resumo r = new GovernancaVOs.Resumo();
            r.setId(o.getId());
            r.setNome(o.getNome() + " (" + o.getInicio().getYear() + (o.getFim().getYear() != o.getInicio().getYear() ? "–" + o.getFim().getYear() : "") + ")");
            return r;
        }).toList();
    }

    public GovernancaVOs.Orcamento obter(Long id) {
        return converter(List.of(visivel(id)), true).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(GovernancaVOs.Orcamento req, Long userId) {
        OrcamentoDO atual = req.getId() == null ? null : visivel(req.getId());
        if (req.getFim().isBefore(req.getInicio())) {
            throw exception(ORCAMENTO_PERIODO);
        }
        OrcamentoDO o = OrcamentoDO.builder().id(req.getId()).nome(req.getNome().trim()).tipo(vazioNulo(req.getTipo()))
                .deptId(req.getDeptId() != null ? req.getDeptId() : atual != null ? atual.getDeptId() : apoio.deptDe(userId))
                .inicio(req.getInicio()).fim(req.getFim()).valor(req.getValor()).observacao(vazioNulo(req.getObservacao())).build();
        if (atual == null) {
            orcamentoMapper.insert(o);
        } else {
            orcamentoMapper.updateById(o);
            orcamentoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrcamentoDO>()
                    .eq(OrcamentoDO::getId, o.getId()).set(OrcamentoDO::getTipo, o.getTipo()).set(OrcamentoDO::getObservacao, o.getObservacao()));
        }
        return o.getId();
    }

    /** Com custos ou projetos (de qualquer unidade), não exclui. */
    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        visivel(id);
        if (emUso(id)) {
            throw exception(ORCAMENTO_EM_USO);
        }
        orcamentoMapper.deleteById(id);
    }

    public boolean emUso(Long id) {
        // projetos de outras unidades também contam (chamada interna: a anotação não valeria, por isso o utilitário)
        return DataPermissionUtils.executeIgnore(() ->
                custoService.orcamentoEmUso(id) || projetoMapper.selectCount(ProjetoDO::getOrcamentoId, id) > 0);
    }

    public Map<Long, String> nomes(Collection<Long> ids) {
        Set<Long> s = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return s.isEmpty() ? new HashMap<>() : orcamentoMapper.selectBatchIds(s).stream().collect(Collectors.toMap(OrcamentoDO::getId, OrcamentoDO::getNome));
    }

    OrcamentoDO visivel(Long id) {
        OrcamentoDO o = id == null ? null : orcamentoMapper.selectById(id);
        if (o == null) {
            throw exception(ORCAMENTO_NAO_EXISTE);
        }
        return o;
    }

    private List<GovernancaVOs.Orcamento> converter(List<OrcamentoDO> lista, boolean detalhe) {
        Map<Long, BigDecimal> gastos = custoService.gastos(lista.stream().map(OrcamentoDO::getId).toList());
        return lista.stream().map(o -> {
            GovernancaVOs.Orcamento v = new GovernancaVOs.Orcamento();
            v.setId(o.getId());
            v.setNome(o.getNome());
            v.setTipo(o.getTipo());
            v.setDeptId(o.getDeptId());
            v.setDept(apoio.nomeDept(o.getDeptId()));
            v.setInicio(o.getInicio());
            v.setFim(o.getFim());
            v.setValor(o.getValor());
            v.setObservacao(o.getObservacao());
            BigDecimal gasto = gastos.getOrDefault(o.getId(), BigDecimal.ZERO);
            v.setGasto(gasto);
            v.setSaldo(o.getValor().subtract(gasto));
            v.setPercentualGasto(o.getValor().signum() == 0 ? (gasto.signum() > 0 ? 100 : 0)
                    : gasto.multiply(BigDecimal.valueOf(100)).divide(o.getValor(), 0, RoundingMode.HALF_UP).intValue());
            if (detalhe) {
                v.setCustos(custoService.doOrcamento(o.getId()));
            }
            return v;
        }).toList();
    }

}
