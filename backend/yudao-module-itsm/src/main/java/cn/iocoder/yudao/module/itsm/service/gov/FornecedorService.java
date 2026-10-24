package cn.iocoder.yudao.module.itsm.service.gov;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ContratoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.FornecedorDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.LicencaDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ContratoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.FornecedorMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.LicencaMapper;
import cn.iocoder.yudao.module.itsm.dominio.Documento;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.itsm.service.gov.CustoService.vazioNulo;

/**
 * Fornecedores (GLPI: Supplier). Cadastro único da empresa, sem escopo de unidade.
 */
@Service
public class FornecedorService {

    @Resource
    private FornecedorMapper fornecedorMapper;
    @Resource
    private ContratoMapper contratoMapper;
    @Resource
    private LicencaMapper licencaMapper;

    public PageResult<GovernancaVOs.Fornecedor> pagina(GovernancaVOs.Filtro f) {
        LambdaQueryWrapperX<FornecedorDO> q = new LambdaQueryWrapperX<>();
        if (f.getBusca() != null && !f.getBusca().isBlank()) {
            String like = "%" + f.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            String doc = Documento.normalizar(f.getBusca());
            q.and(w -> w.apply("nome ILIKE {0}", like).or().apply("tipo ILIKE {0}", like).or().eq(FornecedorDO::getDocumento, doc));
        }
        if ("inativos".equals(f.getSituacao())) {
            q.eq(FornecedorDO::getAtivo, false);
        } else if (!"todos".equals(f.getSituacao())) {
            q.eq(FornecedorDO::getAtivo, true);
        }
        q.orderByAsc(FornecedorDO::getNome);
        PageResult<FornecedorDO> p = fornecedorMapper.selectPage(f, q);
        return new PageResult<>(converter(p.getList()), p.getTotal());
    }

    /** Lista curta para os campos de escolha (ativos). */
    public List<GovernancaVOs.Resumo> simples() {
        return fornecedorMapper.selectList(new LambdaQueryWrapperX<FornecedorDO>().eq(FornecedorDO::getAtivo, true)
                .orderByAsc(FornecedorDO::getNome)).stream().map(x -> {
            GovernancaVOs.Resumo r = new GovernancaVOs.Resumo();
            r.setId(x.getId());
            r.setNome(x.getNome());
            return r;
        }).toList();
    }

    public GovernancaVOs.Fornecedor obter(Long id) {
        return converter(List.of(existente(id))).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(GovernancaVOs.Fornecedor req) {
        if (req.getId() != null) {
            existente(req.getId());
        }
        String doc = Documento.normalizar(req.getDocumento());
        if (doc != null && !Documento.valido(doc)) {
            throw exception(FORNECEDOR_DOCUMENTO_INVALIDO);
        }
        if (doc != null) {
            FornecedorDO mesmo = fornecedorMapper.selectOne(FornecedorDO::getDocumento, doc);
            if (mesmo != null && !mesmo.getId().equals(req.getId())) {
                throw exception(FORNECEDOR_DOCUMENTO_DUPLICADO);
            }
        }
        FornecedorDO f = FornecedorDO.builder().id(req.getId()).nome(req.getNome().trim()).tipo(vazioNulo(req.getTipo()))
                .documento(doc).email(vazioNulo(req.getEmail())).telefone(vazioNulo(req.getTelefone()))
                .site(vazioNulo(req.getSite())).contato(vazioNulo(req.getContato())).endereco(vazioNulo(req.getEndereco()))
                .cidade(vazioNulo(req.getCidade())).uf(req.getUf() == null || req.getUf().isBlank() ? null : req.getUf().toUpperCase(Locale.ROOT))
                .cep(vazioNulo(req.getCep())).observacao(vazioNulo(req.getObservacao()))
                .ativo(!Boolean.FALSE.equals(req.getAtivo())).build();
        if (req.getId() == null) {
            fornecedorMapper.insert(f);
        } else {
            fornecedorMapper.updateById(f);
            // campos opcionais podem voltar a vazio
            fornecedorMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FornecedorDO>()
                    .eq(FornecedorDO::getId, f.getId())
                    .set(FornecedorDO::getTipo, f.getTipo()).set(FornecedorDO::getDocumento, f.getDocumento())
                    .set(FornecedorDO::getEmail, f.getEmail()).set(FornecedorDO::getTelefone, f.getTelefone())
                    .set(FornecedorDO::getSite, f.getSite()).set(FornecedorDO::getContato, f.getContato())
                    .set(FornecedorDO::getEndereco, f.getEndereco()).set(FornecedorDO::getCidade, f.getCidade())
                    .set(FornecedorDO::getUf, f.getUf()).set(FornecedorDO::getCep, f.getCep())
                    .set(FornecedorDO::getObservacao, f.getObservacao()));
        }
        return f.getId();
    }

    /** Em uso (em qualquer unidade) não exclui: desativa-se. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void excluir(Long id) {
        existente(id);
        if (contratoMapper.selectCount(ContratoDO::getFornecedorId, id) > 0 || licencaMapper.selectCount(LicencaDO::getFornecedorId, id) > 0) {
            throw exception(FORNECEDOR_EM_USO);
        }
        fornecedorMapper.deleteById(id);
    }

    public Map<Long, String> nomes(Collection<Long> ids) {
        Set<Long> s = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return s.isEmpty() ? new HashMap<>() : fornecedorMapper.selectBatchIds(s).stream()
                .collect(Collectors.toMap(FornecedorDO::getId, FornecedorDO::getNome));
    }

    public void exigir(Long id) {
        if (id != null) {
            existente(id);
        }
    }

    private FornecedorDO existente(Long id) {
        FornecedorDO f = id == null ? null : fornecedorMapper.selectById(id);
        if (f == null) {
            throw exception(FORNECEDOR_NAO_EXISTE);
        }
        return f;
    }

    private List<GovernancaVOs.Fornecedor> converter(List<FornecedorDO> lista) {
        Set<Long> ids = lista.stream().map(FornecedorDO::getId).collect(Collectors.toSet());
        // contagem visível ao usuário (contratos e licenças seguem o escopo de unidade)
        Map<Long, Long> contratos = ids.isEmpty() ? Map.of() : contratoMapper.selectList(new LambdaQueryWrapperX<ContratoDO>()
                .in(ContratoDO::getFornecedorId, ids)).stream().collect(Collectors.groupingBy(ContratoDO::getFornecedorId, Collectors.counting()));
        Map<Long, Long> licencas = ids.isEmpty() ? Map.of() : licencaMapper.selectList(new LambdaQueryWrapperX<LicencaDO>()
                .in(LicencaDO::getFornecedorId, ids)).stream().collect(Collectors.groupingBy(LicencaDO::getFornecedorId, Collectors.counting()));
        return lista.stream().map(f -> {
            GovernancaVOs.Fornecedor v = BeanUtils.toBean(f, GovernancaVOs.Fornecedor.class);
            v.setTotalContratos(contratos.getOrDefault(f.getId(), 0L).intValue());
            v.setTotalLicencas(licencas.getOrDefault(f.getId(), 0L).intValue());
            return v;
        }).toList();
    }

}
