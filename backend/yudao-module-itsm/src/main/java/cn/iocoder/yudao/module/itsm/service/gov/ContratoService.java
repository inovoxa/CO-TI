package cn.iocoder.yudao.module.itsm.service.gov;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ContratoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ContratoRenovacaoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.LicencaDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ContratoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ContratoRenovacaoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.LicencaMapper;
import cn.iocoder.yudao.module.itsm.dominio.Vigencia;
import cn.iocoder.yudao.module.itsm.service.ItilApoio;
import cn.iocoder.yudao.module.itsm.service.email.NotificacaoEmailService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.itsm.service.gov.CustoService.vazioNulo;

/**
 * Contratos (GLPI: Contract): vigência, aviso prévio, renovação tácita ou expressa, custos e aviso por e-mail ao
 * responsável antes do prazo. A situação é calculada (dominio.Vigencia), por isso a fila filtra e pagina em memória:
 * uma empresa tem centenas de contratos, não milhões.
 */
@Slf4j
@Service
public class ContratoService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Resource
    private ContratoMapper contratoMapper;
    @Resource
    private ContratoRenovacaoMapper contratoRenovacaoMapper;
    @Resource
    private LicencaMapper licencaMapper;
    @Resource
    private FornecedorService fornecedorService;
    @Resource
    private CustoService custoService;
    @Resource
    private ItilApoio apoio;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private NotificacaoEmailService notificacaoEmailService;

    public PageResult<GovernancaVOs.Contrato> pagina(GovernancaVOs.Filtro f) {
        LambdaQueryWrapperX<ContratoDO> q = new LambdaQueryWrapperX<ContratoDO>()
                .eqIfPresent(ContratoDO::getFornecedorId, f.getFornecedorId()).eqIfPresent(ContratoDO::getDeptId, f.getDeptId());
        if (f.getBusca() != null && !f.getBusca().isBlank()) {
            String like = "%" + f.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("nome ILIKE {0}", like).or().apply("numero ILIKE {0}", like).or().apply("tipo ILIKE {0}", like));
        }
        List<GovernancaVOs.Contrato> todos = converter(contratoMapper.selectList(q), false).stream()
                .filter(c -> f.getSituacao() == null || f.getSituacao().isBlank() || f.getSituacao().equalsIgnoreCase(c.getSituacao()))
                // os mais urgentes primeiro: perto do prazo, vencidos, vigentes pelo prazo; encerrados no fim
                .sorted(Comparator.comparing((GovernancaVOs.Contrato c) -> ordem(c.getSituacao())).thenComparing(GovernancaVOs.Contrato::getPrazo))
                .toList();
        int de = Math.min((f.getPageNo() - 1) * f.getPageSize(), todos.size());
        return new PageResult<>(todos.subList(de, Math.min(de + f.getPageSize(), todos.size())), (long) todos.size());
    }

    public List<GovernancaVOs.Resumo> simples() {
        return contratoMapper.selectList(new LambdaQueryWrapperX<ContratoDO>().eq(ContratoDO::getEncerrado, false)
                .orderByAsc(ContratoDO::getNome)).stream().map(c -> {
            GovernancaVOs.Resumo r = new GovernancaVOs.Resumo();
            r.setId(c.getId());
            r.setNome(c.getNome() + (c.getNumero() == null ? "" : " (" + c.getNumero() + ")"));
            return r;
        }).toList();
    }

    public GovernancaVOs.Contrato obter(Long id) {
        return converter(List.of(visivel(id)), true).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(GovernancaVOs.Contrato req, Long userId) {
        ContratoDO atual = req.getId() == null ? null : visivel(req.getId());
        fornecedorService.exigir(req.getFornecedorId());
        if (req.getResponsavelId() != null) {
            adminUserApi.validateUser(req.getResponsavelId());
        }
        int aviso = req.getAvisoPrevioMeses() == null ? 0 : req.getAvisoPrevioMeses();
        if (aviso >= req.getDuracaoMeses()) {
            throw exception(CONTRATO_DADOS_INVALIDOS, "O aviso prévio precisa ser menor que a duração");
        }
        ContratoDO c = ContratoDO.builder().id(req.getId()).nome(req.getNome().trim()).numero(vazioNulo(req.getNumero()))
                .tipo(vazioNulo(req.getTipo())).fornecedorId(req.getFornecedorId())
                .deptId(req.getDeptId() != null ? req.getDeptId() : atual != null ? atual.getDeptId() : apoio.deptDe(userId))
                .responsavelId(req.getResponsavelId()).inicio(req.getInicio()).duracaoMeses(req.getDuracaoMeses())
                .avisoPrevioMeses(aviso).renovacao(req.getRenovacao() == null ? 0 : req.getRenovacao())
                .alertaDias(req.getAlertaDias() == null ? 30 : req.getAlertaDias())
                .numeroContabil(vazioNulo(req.getNumeroContabil())).observacao(vazioNulo(req.getObservacao()))
                .encerrado(Boolean.TRUE.equals(req.getEncerrado())).build();
        if (atual == null) {
            contratoMapper.insert(c);
        } else {
            // datas mudaram: o aviso já enviado pode não valer mais para o novo prazo (o job compara a referência)
            contratoMapper.updateById(c);
            contratoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ContratoDO>()
                    .eq(ContratoDO::getId, c.getId())
                    .set(ContratoDO::getNumero, c.getNumero()).set(ContratoDO::getTipo, c.getTipo())
                    .set(ContratoDO::getFornecedorId, c.getFornecedorId()).set(ContratoDO::getResponsavelId, c.getResponsavelId())
                    .set(ContratoDO::getNumeroContabil, c.getNumeroContabil()).set(ContratoDO::getObservacao, c.getObservacao()));
        }
        return c.getId();
    }

    /** Renovação expressa: soma os meses à duração e registra quem renovou. */
    @Transactional(rollbackFor = Exception.class)
    public void renovar(GovernancaVOs.RenovarReq req) {
        ContratoDO c = visivel(req.getContratoId());
        if (c.getRenovacao() != Vigencia.EXPRESSA || Boolean.TRUE.equals(c.getEncerrado())) {
            throw exception(CONTRATO_RENOVACAO_INVALIDA);
        }
        LocalDate fimAntes = estado(c, LocalDate.now()).fim();
        c.setDuracaoMeses(c.getDuracaoMeses() + req.getMeses());
        contratoMapper.updateById(ContratoDO.builder().id(c.getId()).duracaoMeses(c.getDuracaoMeses()).build());
        contratoRenovacaoMapper.insert(ContratoRenovacaoDO.builder().contratoId(c.getId()).meses(req.getMeses())
                .fimAntes(fimAntes).fimDepois(estado(c, LocalDate.now()).fim()).observacao(vazioNulo(req.getObservacao())).build());
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        visivel(id);
        custoService.apagarDe(CustoService.CONTRATO, id);
        contratoRenovacaoMapper.delete(ContratoRenovacaoDO::getContratoId, id);
        // licenças continuam; só perdem a ligação com o contrato
        licencaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LicencaDO>()
                .eq(LicencaDO::getContratoId, id).set(LicencaDO::getContratoId, null));
        contratoMapper.deleteById(id);
    }

    /**
     * Avisa o responsável quando o contrato chega à data de alerta (GLPI: Contract::cronContract). Um aviso por
     * prazo: na renovação tácita o prazo muda a cada período e o aviso volta a sair.
     *
     * @return quantos contratos geraram aviso
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public int enviarAlertas(LocalDate hoje) {
        int n = 0;
        List<ContratoDO> lista = contratoMapper.selectList(new LambdaQueryWrapperX<ContratoDO>()
                .eq(ContratoDO::getEncerrado, false).isNotNull(ContratoDO::getResponsavelId));
        Map<Long, String> fornecedores = fornecedorService.nomes(lista.stream().map(ContratoDO::getFornecedorId).toList());
        for (ContratoDO c : lista) {
            Vigencia.Estado e = estado(c, hoje);
            if (hoje.isBefore(e.dataAlerta()) || hoje.isAfter(e.fim()) || e.prazo().equals(c.getAlertaReferencia())) {
                continue;
            }
            String oque = c.getAvisoPrevioMeses() > 0 ? "último dia para avisar o fornecedor (cancelar ou renegociar)"
                    : c.getRenovacao() == Vigencia.TACITA ? "fim do período (renova sozinho)" : "fim da vigência";
            notificacaoEmailService.avisar("contrato-vencendo", List.of(c.getResponsavelId()), Map.of(
                    "contrato.nome", c.getNome(), "contrato.numero", c.getNumero() == null ? "" : c.getNumero(),
                    "contrato.fornecedor", fornecedores.getOrDefault(c.getFornecedorId(), "sem fornecedor"),
                    "contrato.prazo", DATA.format(e.prazo()), "contrato.oque", oque), "/governanca/contratos?id=" + c.getId());
            contratoMapper.updateById(ContratoDO.builder().id(c.getId()).alertaReferencia(e.prazo()).build());
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ internos

    static Vigencia.Estado estado(ContratoDO c, LocalDate hoje) {
        return Vigencia.calcular(c.getInicio(), c.getDuracaoMeses(), c.getAvisoPrevioMeses(), c.getRenovacao(),
                c.getAlertaDias(), Boolean.TRUE.equals(c.getEncerrado()), hoje);
    }

    private static int ordem(String situacao) {
        return switch (situacao) {
            case "a_vencer" -> 0;
            case "vencido" -> 1;
            case "vigente" -> 2;
            case "futuro" -> 3;
            default -> 4;
        };
    }

    ContratoDO visivel(Long id) {
        ContratoDO c = id == null ? null : contratoMapper.selectById(id);
        if (c == null) {
            throw exception(CONTRATO_NAO_EXISTE);
        }
        return c;
    }

    private List<GovernancaVOs.Contrato> converter(List<ContratoDO> lista, boolean detalhe) {
        LocalDate hoje = LocalDate.now();
        Set<Long> ids = lista.stream().map(ContratoDO::getId).collect(Collectors.toSet());
        Map<Long, String> fornecedores = fornecedorService.nomes(lista.stream().map(ContratoDO::getFornecedorId).toList());
        Set<Long> pessoas = lista.stream().map(ContratoDO::getResponsavelId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        Map<Long, BigDecimal> custos = custoService.totais(CustoService.CONTRATO, ids);
        Map<Long, Long> licencas = ids.isEmpty() ? Map.of() : licencaMapper.selectList(new LambdaQueryWrapperX<LicencaDO>()
                .in(LicencaDO::getContratoId, ids)).stream().collect(Collectors.groupingBy(LicencaDO::getContratoId, Collectors.counting()));
        return lista.stream().map(c -> {
            GovernancaVOs.Contrato v = new GovernancaVOs.Contrato();
            v.setId(c.getId());
            v.setNome(c.getNome());
            v.setNumero(c.getNumero());
            v.setTipo(c.getTipo());
            v.setFornecedorId(c.getFornecedorId());
            v.setFornecedor(fornecedores.get(c.getFornecedorId()));
            v.setDeptId(c.getDeptId());
            v.setDept(apoio.nomeDept(c.getDeptId()));
            v.setResponsavelId(c.getResponsavelId());
            v.setResponsavel(Optional.ofNullable(nomes.get(c.getResponsavelId())).map(AdminUserRespDTO::getNickname).orElse(null));
            v.setInicio(c.getInicio());
            v.setDuracaoMeses(c.getDuracaoMeses());
            v.setAvisoPrevioMeses(c.getAvisoPrevioMeses());
            v.setRenovacao(c.getRenovacao());
            v.setRenovacaoRotulo(Vigencia.rotuloRenovacao(c.getRenovacao()));
            v.setAlertaDias(c.getAlertaDias());
            v.setNumeroContabil(c.getNumeroContabil());
            v.setObservacao(c.getObservacao());
            v.setEncerrado(c.getEncerrado());
            Vigencia.Estado e = estado(c, hoje);
            v.setFim(e.fim());
            v.setPrazoAviso(e.prazoAviso());
            v.setPrazo(e.prazo());
            v.setDataAlerta(e.dataAlerta());
            v.setPeriodo(e.periodo());
            v.setSituacao(e.situacao().name().toLowerCase(Locale.ROOT));
            v.setSituacaoRotulo(e.situacao().getRotulo());
            v.setCustoTotal(custos.getOrDefault(c.getId(), BigDecimal.ZERO));
            v.setTotalLicencas(licencas.getOrDefault(c.getId(), 0L).intValue());
            if (detalhe) {
                v.setRenovacoes(renovacoes(c.getId()));
            }
            return v;
        }).toList();
    }

    private List<GovernancaVOs.Renovacao> renovacoes(Long contratoId) {
        List<ContratoRenovacaoDO> lista = contratoRenovacaoMapper.selectList(new LambdaQueryWrapperX<ContratoRenovacaoDO>()
                .eq(ContratoRenovacaoDO::getContratoId, contratoId).orderByDesc(ContratoRenovacaoDO::getId));
        Set<Long> quem = lista.stream().map(r -> parse(r.getCreator())).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(quem);
        return lista.stream().map(r -> {
            GovernancaVOs.Renovacao v = new GovernancaVOs.Renovacao();
            v.setId(r.getId());
            v.setMeses(r.getMeses());
            v.setFimAntes(r.getFimAntes());
            v.setFimDepois(r.getFimDepois());
            v.setObservacao(r.getObservacao());
            v.setQuando(r.getCreateTime());
            Long u = parse(r.getCreator());
            v.setQuem(u == null ? null : Optional.ofNullable(nomes.get(u)).map(AdminUserRespDTO::getNickname).orElse("#" + u));
            return v;
        }).toList();
    }

    private static Long parse(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
