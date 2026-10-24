package cn.iocoder.yudao.module.itsm.service.gov;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ContratoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.LicencaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.LicencaUsoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ContratoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.LicencaMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.LicencaUsoMapper;
import cn.iocoder.yudao.module.itsm.service.ItilApoio;
import cn.iocoder.yudao.module.itsm.service.email.NotificacaoEmailService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.itsm.service.gov.CustoService.vazioNulo;

/**
 * Licenças de software (GLPI: SoftwareLicense): quantidade, quem usa, validade e chave cifrada.
 *
 * <p>A chave de ativação é um segredo da empresa: fica cifrada com a chave-mestra, nunca volta na consulta e só é
 * revelada a quem tem {@code itsm:licenca:ver-chave}, com registro na trilha de auditoria.
 */
@Service
public class LicencaService {

    public static final String CONTEXTO_CHAVE = "licenca-chave";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Resource
    private LicencaMapper licencaMapper;
    @Resource
    private LicencaUsoMapper licencaUsoMapper;
    @Resource
    private ContratoMapper contratoMapper;
    @Resource
    private FornecedorService fornecedorService;
    @Resource
    private CustoService custoService;
    @Resource
    private ItilApoio apoio;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    private NotificacaoEmailService notificacaoEmailService;

    public PageResult<GovernancaVOs.Licenca> pagina(GovernancaVOs.Filtro f) {
        LambdaQueryWrapperX<LicencaDO> q = new LambdaQueryWrapperX<LicencaDO>()
                .eqIfPresent(LicencaDO::getFornecedorId, f.getFornecedorId()).eqIfPresent(LicencaDO::getDeptId, f.getDeptId());
        if (f.getBusca() != null && !f.getBusca().isBlank()) {
            String like = "%" + f.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("software ILIKE {0}", like).or().apply("nome ILIKE {0}", like).or().apply("fabricante ILIKE {0}", like));
        }
        List<GovernancaVOs.Licenca> todas = converter(licencaMapper.selectList(q), false).stream()
                .filter(l -> f.getSituacao() == null || f.getSituacao().isBlank()
                        || ("excedida".equals(f.getSituacao()) ? Boolean.TRUE.equals(l.getExcedida()) : f.getSituacao().equals(l.getSituacao())))
                .sorted(Comparator.comparing(GovernancaVOs.Licenca::getSoftware, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int de = Math.min((f.getPageNo() - 1) * f.getPageSize(), todas.size());
        return new PageResult<>(todas.subList(de, Math.min(de + f.getPageSize(), todas.size())), (long) todas.size());
    }

    public GovernancaVOs.Licenca obter(Long id) {
        return converter(List.of(visivel(id)), true).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(GovernancaVOs.Licenca req, Long userId) {
        LicencaDO atual = req.getId() == null ? null : visivel(req.getId());
        fornecedorService.exigir(req.getFornecedorId());
        if (req.getContratoId() != null && contratoMapper.selectById(req.getContratoId()) == null) {
            throw exception(CONTRATO_NAO_EXISTE);
        }
        if (req.getResponsavelId() != null) {
            adminUserApi.validateUser(req.getResponsavelId());
        }
        boolean excesso = Boolean.TRUE.equals(req.getPermiteExcesso());
        if (atual != null && req.getQuantidade() >= 0 && !excesso) {
            long emUso = licencaUsoMapper.selectCount(LicencaUsoDO::getLicencaId, atual.getId());
            if (emUso > req.getQuantidade()) {
                throw exception(LICENCA_QUANTIDADE_MENOR, emUso);
            }
        }
        LicencaDO l = LicencaDO.builder().id(req.getId()).software(req.getSoftware().trim()).fabricante(vazioNulo(req.getFabricante()))
                .nome(vazioNulo(req.getNome())).tipo(vazioNulo(req.getTipo())).quantidade(req.getQuantidade()).permiteExcesso(excesso)
                .expira(req.getExpira()).alertaDias(req.getAlertaDias() == null ? 30 : req.getAlertaDias())
                .fornecedorId(req.getFornecedorId()).contratoId(req.getContratoId())
                .deptId(req.getDeptId() != null ? req.getDeptId() : atual != null ? atual.getDeptId() : apoio.deptDe(userId))
                .responsavelId(req.getResponsavelId()).observacao(vazioNulo(req.getObservacao()))
                .ativo(!Boolean.FALSE.equals(req.getAtivo())).build();
        String chave = vazioNulo(req.getChave());
        if (chave != null) {
            if (!cifrador.configurado()) {
                throw exception(GOVERNANCA_SEM_CHAVE_MESTRA);
            }
            l.setChaveCifrada(cifrador.cifrar(chave, CONTEXTO_CHAVE));
        }
        if (atual == null) {
            licencaMapper.insert(l);
        } else {
            licencaMapper.updateById(l);
            var u = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LicencaDO>().eq(LicencaDO::getId, l.getId())
                    .set(LicencaDO::getFabricante, l.getFabricante()).set(LicencaDO::getNome, l.getNome())
                    .set(LicencaDO::getTipo, l.getTipo()).set(LicencaDO::getExpira, l.getExpira())
                    .set(LicencaDO::getFornecedorId, l.getFornecedorId()).set(LicencaDO::getContratoId, l.getContratoId())
                    .set(LicencaDO::getResponsavelId, l.getResponsavelId()).set(LicencaDO::getObservacao, l.getObservacao());
            if (chave == null && Boolean.TRUE.equals(req.getRemoverChave())) {
                u.set(LicencaDO::getChaveCifrada, null);
            }
            licencaMapper.update(null, u);
        }
        return l.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        visivel(id);
        licencaUsoMapper.delete(LicencaUsoDO::getLicencaId, id);
        custoService.apagarDe(CustoService.LICENCA, id);
        licencaMapper.deleteById(id);
    }

    /** Distribui a licença. Sem excesso permitido, não passa da quantidade comprada. */
    @Transactional(rollbackFor = Exception.class)
    public Long adicionarUso(GovernancaVOs.Uso req) {
        if ((req.getUserId() == null) == (vazioNulo(req.getEquipamento()) == null)) {
            throw exception(LICENCA_USO_INVALIDO);
        }
        LicencaDO l = licencaMapper.selectByIdParaAtualizar(req.getLicencaId());
        if (l == null || visivelOuNulo(l.getId()) == null) {
            throw exception(LICENCA_NAO_EXISTE);
        }
        if (req.getUserId() != null) {
            adminUserApi.validateUser(req.getUserId());
        }
        List<LicencaUsoDO> usos = licencaUsoMapper.selectList(LicencaUsoDO::getLicencaId, l.getId());
        String equip = vazioNulo(req.getEquipamento());
        boolean repetido = usos.stream().anyMatch(u -> (req.getUserId() != null && req.getUserId().equals(u.getUserId()))
                || (equip != null && equip.equalsIgnoreCase(u.getEquipamento())));
        if (repetido) {
            throw exception(LICENCA_USO_DUPLICADO);
        }
        if (l.getQuantidade() >= 0 && usos.size() >= l.getQuantidade() && !Boolean.TRUE.equals(l.getPermiteExcesso())) {
            throw exception(LICENCA_ESGOTADA, l.getQuantidade());
        }
        LicencaUsoDO u = LicencaUsoDO.builder().licencaId(l.getId()).userId(req.getUserId()).equipamento(equip)
                .desde(req.getDesde() != null ? req.getDesde() : LocalDate.now()).observacao(vazioNulo(req.getObservacao())).build();
        licencaUsoMapper.insert(u);
        return u.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void removerUso(Long usoId) {
        LicencaUsoDO u = usoId == null ? null : licencaUsoMapper.selectById(usoId);
        if (u == null) {
            throw exception(LICENCA_USO_NAO_EXISTE);
        }
        visivel(u.getLicencaId());
        licencaUsoMapper.deleteById(usoId);
    }

    /** Revela a chave e registra na trilha de auditoria quem viu. */
    public String revelarChave(Long id, Long userId) {
        LicencaDO l = visivel(id);
        if (l.getChaveCifrada() == null) {
            throw exception(LICENCA_SEM_CHAVE);
        }
        String chave = cifrador.decifrar(l.getChaveCifrada(), CONTEXTO_CHAVE);
        auditoria.registrar("licenca.chave_vista", userId, null, Map.of("licenca", id, "software", l.getSoftware()));
        return chave;
    }

    /** Licenças usadas por uma pessoa (para o perfil e, depois, o inventário). */
    public List<GovernancaVOs.Licenca> daPessoa(Long userId) {
        Set<Long> ids = licencaUsoMapper.selectList(LicencaUsoDO::getUserId, userId).stream().map(LicencaUsoDO::getLicencaId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? List.of() : converter(licencaMapper.selectBatchIds(ids), false);
    }

    /** Aviso ao responsável antes de expirar; um por data de expiração. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public int enviarAlertas(LocalDate hoje) {
        int n = 0;
        for (LicencaDO l : licencaMapper.selectList(new LambdaQueryWrapperX<LicencaDO>().eq(LicencaDO::getAtivo, true)
                .isNotNull(LicencaDO::getExpira).isNotNull(LicencaDO::getResponsavelId))) {
            if (hoje.isBefore(l.getExpira().minusDays(l.getAlertaDias())) || hoje.isAfter(l.getExpira())
                    || l.getExpira().equals(l.getAlertaReferencia())) {
                continue;
            }
            long emUso = licencaUsoMapper.selectCount(LicencaUsoDO::getLicencaId, l.getId());
            notificacaoEmailService.avisar("licenca-vencendo", List.of(l.getResponsavelId()), Map.of(
                    "licenca.nome", l.getSoftware() + (l.getNome() == null ? "" : " — " + l.getNome()),
                    "licenca.expira", DATA.format(l.getExpira()),
                    "licenca.uso", emUso + " de " + (l.getQuantidade() < 0 ? "ilimitadas" : l.getQuantidade())),
                    "/governanca/licencas?id=" + l.getId());
            licencaMapper.updateById(LicencaDO.builder().id(l.getId()).alertaReferencia(l.getExpira()).build());
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ internos

    LicencaDO visivel(Long id) {
        LicencaDO l = visivelOuNulo(id);
        if (l == null) {
            throw exception(LICENCA_NAO_EXISTE);
        }
        return l;
    }

    private LicencaDO visivelOuNulo(Long id) {
        // selectById passa pelo escopo de unidade
        return id == null ? null : licencaMapper.selectById(id);
    }

    private List<GovernancaVOs.Licenca> converter(List<LicencaDO> lista, boolean detalhe) {
        LocalDate hoje = LocalDate.now();
        Set<Long> ids = lista.stream().map(LicencaDO::getId).collect(Collectors.toSet());
        List<LicencaUsoDO> usos = ids.isEmpty() ? List.of() : licencaUsoMapper.selectList(new LambdaQueryWrapperX<LicencaUsoDO>()
                .in(LicencaUsoDO::getLicencaId, ids).orderByAsc(LicencaUsoDO::getId));
        Map<Long, List<LicencaUsoDO>> porLicenca = usos.stream().collect(Collectors.groupingBy(LicencaUsoDO::getLicencaId));
        Map<Long, String> fornecedores = fornecedorService.nomes(lista.stream().map(LicencaDO::getFornecedorId).toList());
        Set<Long> contratoIds = lista.stream().map(LicencaDO::getContratoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> contratos = contratoIds.isEmpty() ? Map.of() : contratoMapper.selectBatchIds(contratoIds).stream()
                .collect(Collectors.toMap(ContratoDO::getId, ContratoDO::getNome));
        Set<Long> pessoas = new HashSet<>();
        lista.forEach(l -> pessoas.add(l.getResponsavelId()));
        if (detalhe) {
            usos.forEach(u -> pessoas.add(u.getUserId()));
        }
        pessoas.remove(null);
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        return lista.stream().map(l -> {
            GovernancaVOs.Licenca v = new GovernancaVOs.Licenca();
            v.setId(l.getId());
            v.setSoftware(l.getSoftware());
            v.setFabricante(l.getFabricante());
            v.setNome(l.getNome());
            v.setTipo(l.getTipo());
            v.setQuantidade(l.getQuantidade());
            v.setPermiteExcesso(l.getPermiteExcesso());
            v.setChaveDefinida(l.getChaveCifrada() != null);
            v.setExpira(l.getExpira());
            v.setAlertaDias(l.getAlertaDias());
            v.setFornecedorId(l.getFornecedorId());
            v.setFornecedor(fornecedores.get(l.getFornecedorId()));
            v.setContratoId(l.getContratoId());
            v.setContrato(l.getContratoId() == null ? null : contratos.getOrDefault(l.getContratoId(), "#" + l.getContratoId()));
            v.setDeptId(l.getDeptId());
            v.setDept(apoio.nomeDept(l.getDeptId()));
            v.setResponsavelId(l.getResponsavelId());
            v.setResponsavel(Optional.ofNullable(nomes.get(l.getResponsavelId())).map(AdminUserRespDTO::getNickname).orElse(null));
            v.setObservacao(l.getObservacao());
            v.setAtivo(l.getAtivo());
            List<LicencaUsoDO> meus = porLicenca.getOrDefault(l.getId(), List.of());
            v.setEmUso(meus.size());
            v.setDisponivel(l.getQuantidade() < 0 ? null : Math.max(0, l.getQuantidade() - meus.size()));
            v.setExcedida(l.getQuantidade() >= 0 && meus.size() > l.getQuantidade());
            String s;
            if (!Boolean.TRUE.equals(l.getAtivo())) {
                s = "inativa";
            } else if (l.getExpira() != null && hoje.isAfter(l.getExpira())) {
                s = "vencida";
            } else if (l.getExpira() != null && !hoje.isBefore(l.getExpira().minusDays(l.getAlertaDias()))) {
                s = "a_vencer";
            } else {
                s = "vigente";
            }
            v.setSituacao(s);
            v.setSituacaoRotulo(switch (s) {
                case "inativa" -> "Inativa";
                case "vencida" -> "Expirada";
                case "a_vencer" -> "Perto de expirar";
                default -> "Vigente";
            });
            if (detalhe) {
                v.setUsos(meus.stream().map(u -> {
                    GovernancaVOs.Uso x = new GovernancaVOs.Uso();
                    x.setId(u.getId());
                    x.setLicencaId(u.getLicencaId());
                    x.setUserId(u.getUserId());
                    x.setEquipamento(u.getEquipamento());
                    x.setDesde(u.getDesde());
                    x.setObservacao(u.getObservacao());
                    x.setNome(u.getUserId() != null ? Optional.ofNullable(nomes.get(u.getUserId())).map(AdminUserRespDTO::getNickname)
                            .orElse("#" + u.getUserId()) : u.getEquipamento());
                    return x;
                }).toList());
            }
            return v;
        }).toList();
    }

}
