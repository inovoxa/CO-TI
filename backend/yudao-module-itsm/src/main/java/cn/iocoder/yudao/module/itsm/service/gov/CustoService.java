package cn.iocoder.yudao.module.itsm.service.gov;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Custos lançados em contratos, projetos e licenças (GLPI: ContractCost, ProjectCost). Quem lança precisa poder
 * editar o item de origem e enxergá-lo no próprio escopo de unidade; o orçamento escolhido também.
 */
@Service
public class CustoService {

    public static final String CONTRATO = "contrato";
    public static final String PROJETO = "projeto";
    public static final String LICENCA = "licenca";

    private static final Map<String, String> PERMISSAO = Map.of(
            CONTRATO, "itsm:contrato:update", PROJETO, "itsm:projeto:update", LICENCA, "itsm:licenca:update");

    @Resource
    private CustoMapper custoMapper;
    @Resource
    private ContratoMapper contratoMapper;
    @Resource
    private ProjetoMapper projetoMapper;
    @Resource
    private LicencaMapper licencaMapper;
    @Resource
    private OrcamentoMapper orcamentoMapper;
    @Resource
    private PermissionApi permissionApi;

    public List<GovernancaVOs.Custo> listar(String objeto, Long objetoId) {
        nomeDaOrigem(objeto, objetoId);   // existe e está no escopo
        return converter(custoMapper.selectList(new LambdaQueryWrapperX<CustoDO>().eq(CustoDO::getObjeto, objeto)
                .eq(CustoDO::getObjetoId, objetoId).orderByAsc(CustoDO::getInicio).orderByAsc(CustoDO::getId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(GovernancaVOs.Custo req, Long userId) {
        exigirPermissao(req.getObjeto(), userId);
        nomeDaOrigem(req.getObjeto(), req.getObjetoId());
        if (req.getFim() != null && req.getFim().isBefore(req.getInicio())) {
            throw exception(CUSTO_PERIODO);
        }
        if (req.getOrcamentoId() != null && orcamentoMapper.selectById(req.getOrcamentoId()) == null) {
            throw exception(ORCAMENTO_NAO_EXISTE);
        }
        CustoDO c = CustoDO.builder().id(req.getId()).objeto(req.getObjeto()).objetoId(req.getObjetoId())
                .nome(req.getNome().trim()).inicio(req.getInicio()).fim(req.getFim()).valor(req.getValor())
                .orcamentoId(req.getOrcamentoId()).observacao(vazioNulo(req.getObservacao())).build();
        if (req.getId() == null) {
            custoMapper.insert(c);
        } else {
            CustoDO atual = obter(req.getId());
            if (!atual.getObjeto().equals(req.getObjeto()) || !atual.getObjetoId().equals(req.getObjetoId())) {
                throw exception(CUSTO_NAO_EXISTE);
            }
            custoMapper.updateById(c);
            // fim e orçamento podem voltar a vazio
            custoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CustoDO>()
                    .eq(CustoDO::getId, c.getId()).set(CustoDO::getFim, c.getFim()).set(CustoDO::getOrcamentoId, c.getOrcamentoId()));
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id, Long userId) {
        CustoDO c = obter(id);
        exigirPermissao(c.getObjeto(), userId);
        nomeDaOrigem(c.getObjeto(), c.getObjetoId());
        custoMapper.deleteById(id);
    }

    /** Soma por item de origem. */
    public Map<Long, BigDecimal> totais(String objeto, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return custoMapper.selectList(new LambdaQueryWrapperX<CustoDO>().eq(CustoDO::getObjeto, objeto).in(CustoDO::getObjetoId, ids))
                .stream().collect(Collectors.groupingBy(CustoDO::getObjetoId,
                        Collectors.reducing(BigDecimal.ZERO, CustoDO::getValor, BigDecimal::add)));
    }

    /** Gasto por orçamento (soma de todos os custos ligados, de qualquer origem). */
    public Map<Long, BigDecimal> gastos(Collection<Long> orcamentoIds) {
        if (orcamentoIds.isEmpty()) {
            return Map.of();
        }
        return custoMapper.selectList(new LambdaQueryWrapperX<CustoDO>().in(CustoDO::getOrcamentoId, orcamentoIds))
                .stream().collect(Collectors.groupingBy(CustoDO::getOrcamentoId,
                        Collectors.reducing(BigDecimal.ZERO, CustoDO::getValor, BigDecimal::add)));
    }

    public List<GovernancaVOs.Custo> doOrcamento(Long orcamentoId) {
        return converter(custoMapper.selectList(new LambdaQueryWrapperX<CustoDO>().eq(CustoDO::getOrcamentoId, orcamentoId)
                .orderByAsc(CustoDO::getInicio)));
    }

    public boolean orcamentoEmUso(Long orcamentoId) {
        return custoMapper.selectCount(CustoDO::getOrcamentoId, orcamentoId) > 0;
    }

    public void apagarDe(String objeto, Long objetoId) {
        custoMapper.delete(new LambdaQueryWrapperX<CustoDO>().eq(CustoDO::getObjeto, objeto).eq(CustoDO::getObjetoId, objetoId));
    }

    private List<GovernancaVOs.Custo> converter(List<CustoDO> lista) {
        Set<Long> orcIds = lista.stream().map(CustoDO::getOrcamentoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> orcamentos = orcIds.isEmpty() ? Map.of() : orcamentoMapper.selectBatchIds(orcIds).stream()
                .collect(Collectors.toMap(OrcamentoDO::getId, OrcamentoDO::getNome));
        Map<String, Map<Long, String>> origens = new HashMap<>();
        lista.stream().collect(Collectors.groupingBy(CustoDO::getObjeto, Collectors.mapping(CustoDO::getObjetoId, Collectors.toSet())))
                .forEach((obj, ids) -> origens.put(obj, nomes(obj, ids)));
        return lista.stream().map(c -> {
            GovernancaVOs.Custo v = new GovernancaVOs.Custo();
            v.setId(c.getId());
            v.setObjeto(c.getObjeto());
            v.setObjetoId(c.getObjetoId());
            v.setNome(c.getNome());
            v.setInicio(c.getInicio());
            v.setFim(c.getFim());
            v.setValor(c.getValor());
            v.setOrcamentoId(c.getOrcamentoId());
            v.setObservacao(c.getObservacao());
            v.setOrcamento(c.getOrcamentoId() == null ? null : orcamentos.getOrDefault(c.getOrcamentoId(), "#" + c.getOrcamentoId()));
            // fora do escopo de quem consulta: só o número
            v.setOrigem(origens.getOrDefault(c.getObjeto(), Map.of()).getOrDefault(c.getObjetoId(), "#" + c.getObjetoId()));
            return v;
        }).toList();
    }

    private Map<Long, String> nomes(String objeto, Collection<Long> ids) {
        return switch (objeto) {
            case CONTRATO -> contratoMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(ContratoDO::getId, ContratoDO::getNome));
            case PROJETO -> projetoMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(ProjetoDO::getId, ProjetoDO::getNome));
            default -> licencaMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(LicencaDO::getId,
                    l -> l.getSoftware() + (l.getNome() == null ? "" : " — " + l.getNome())));
        };
    }

    /** Nome do item de origem; falha se não existe ou está fora do escopo de unidade. */
    private String nomeDaOrigem(String objeto, Long id) {
        String nome = id == null ? null : nomes(objeto, List.of(id)).get(id);
        if (nome == null) {
            throw exception(switch (objeto) {
                case CONTRATO -> CONTRATO_NAO_EXISTE;
                case PROJETO -> PROJETO_NAO_EXISTE;
                default -> LICENCA_NAO_EXISTE;
            });
        }
        return nome;
    }

    private void exigirPermissao(String objeto, Long userId) {
        String p = PERMISSAO.get(objeto);
        if (p == null || !permissionApi.hasAnyPermissions(userId, p)) {
            throw exception(CUSTO_SEM_PERMISSAO);
        }
    }

    private CustoDO obter(Long id) {
        CustoDO c = id == null ? null : custoMapper.selectById(id);
        if (c == null) {
            throw exception(CUSTO_NAO_EXISTE);
        }
        return c;
    }

    static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static <T> Map<Long, T> porId(List<T> lista, Function<T, Long> id) {
        return lista.stream().collect(Collectors.toMap(id, Function.identity()));
    }

}
