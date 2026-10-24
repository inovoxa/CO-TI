package cn.iocoder.yudao.module.itsm.service.gov;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import cn.iocoder.yudao.module.itsm.dominio.Projetos;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.enums.MudancaStatus;
import cn.iocoder.yudao.module.itsm.enums.ProblemaStatus;
import cn.iocoder.yudao.module.itsm.service.ChamadoEscopo;
import cn.iocoder.yudao.module.itsm.service.ItilApoio;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.itsm.service.gov.CustoService.vazioNulo;

/**
 * Projetos (GLPI: Project, ProjectTask, ProjectTeam, Itil_Project): tarefas com datas para o Gantt, equipe, custos e
 * ligação com chamados, problemas e mudanças.
 *
 * <p>Quem gerencia ({@code itsm:projeto:update}) mexe em tudo. O responsável por uma tarefa, só com permissão de
 * consulta, atualiza o andamento dela (situação, percentual e horas gastas).
 */
@Service
public class ProjetoService {

    public static final String P_UPDATE = "itsm:projeto:update";

    @Resource
    private ProjetoMapper projetoMapper;
    @Resource
    private ProjetoTarefaMapper projetoTarefaMapper;
    @Resource
    private ProjetoMembroMapper projetoMembroMapper;
    @Resource
    private ProjetoVinculoMapper projetoVinculoMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ProblemaMapper problemaMapper;
    @Resource
    private MudancaMapper mudancaMapper;
    @Resource
    private ChamadoEscopo escopo;
    @Resource
    private OrcamentoService orcamentoService;
    @Resource
    private CustoService custoService;
    @Resource
    private ItilApoio apoio;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private PermissionApi permissionApi;

    private static final String MEUS = "(responsavel_id = {0}"
            + " OR EXISTS (SELECT 1 FROM itsm.projeto_membro m WHERE m.projeto_id = projeto.id AND m.deleted = 0 AND m.user_id = {0})"
            + " OR EXISTS (SELECT 1 FROM itsm.projeto_tarefa t WHERE t.projeto_id = projeto.id AND t.deleted = 0 AND t.responsavel_id = {0}))";

    // ------------------------------------------------------------------ consulta

    public PageResult<GovernancaVOs.Projeto> pagina(GovernancaVOs.Filtro f, Long userId) {
        LambdaQueryWrapperX<ProjetoDO> q = new LambdaQueryWrapperX<ProjetoDO>().eqIfPresent(ProjetoDO::getDeptId, f.getDeptId());
        if (f.getSituacao() != null && f.getSituacao().matches("[1-5]")) {
            q.eq(ProjetoDO::getSituacao, Integer.parseInt(f.getSituacao()));
        } else if ("abertos".equals(f.getSituacao()) || f.getSituacao() == null || f.getSituacao().isBlank()) {
            q.lt(ProjetoDO::getSituacao, Projetos.CONCLUIDO);
        }
        if (f.getBusca() != null && !f.getBusca().isBlank()) {
            String like = "%" + f.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("nome ILIKE {0}", like).or().apply("codigo ILIKE {0}", like));
        }
        if (Boolean.TRUE.equals(f.getMeus())) {
            q.apply(MEUS, userId);
        }
        q.orderByAsc(ProjetoDO::getSituacao).orderByAsc(ProjetoDO::getFimPrevisto).orderByDesc(ProjetoDO::getId);
        PageResult<ProjetoDO> p = projetoMapper.selectPage(f, q);
        return new PageResult<>(converter(p.getList(), null), p.getTotal());
    }

    public GovernancaVOs.Projeto obter(Long id, Long userId) {
        return converter(List.of(visivel(id)), userId).get(0);
    }

    // ------------------------------------------------------------------ projeto

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(GovernancaVOs.Projeto req, Long userId) {
        ProjetoDO atual = req.getId() == null ? null : visivel(req.getId());
        String codigo = vazioNulo(req.getCodigo());
        if (codigo != null) {
            ProjetoDO mesmo = projetoMapper.selectOne(new LambdaQueryWrapperX<ProjetoDO>().apply("lower(codigo) = lower({0})", codigo));
            if (mesmo != null && !mesmo.getId().equals(req.getId())) {
                throw exception(PROJETO_CODIGO_DUPLICADO);
            }
        }
        if (req.getInicioPrevisto() != null && req.getFimPrevisto() != null && req.getFimPrevisto().isBefore(req.getInicioPrevisto())) {
            throw exception(PROJETO_DATAS);
        }
        if (req.getOrcamentoId() != null) {
            orcamentoService.visivel(req.getOrcamentoId());
        }
        Set<Long> pessoas = new LinkedHashSet<>(req.getMembroIds());
        pessoas.remove(null);
        if (req.getResponsavelId() != null) {
            pessoas.add(req.getResponsavelId());
        }
        if (!pessoas.isEmpty()) {
            adminUserApi.validateUserList(pessoas);
        }
        LocalDate hoje = LocalDate.now();
        int situacao = req.getSituacao() == null ? Projetos.PLANEJADO : req.getSituacao();
        ProjetoDO p = ProjetoDO.builder().id(req.getId()).nome(req.getNome().trim()).codigo(codigo)
                .descricao(HtmlSeguro.limpar(req.getDescricao())).situacao(situacao)
                .prioridade(req.getPrioridade() == null ? 3 : req.getPrioridade())
                .deptId(req.getDeptId() != null ? req.getDeptId() : atual != null ? atual.getDeptId() : apoio.deptDe(userId))
                .responsavelId(req.getResponsavelId()).inicioPrevisto(req.getInicioPrevisto()).fimPrevisto(req.getFimPrevisto())
                .inicioReal(req.getInicioReal()).fimReal(req.getFimReal())
                .percentualAuto(!Boolean.FALSE.equals(req.getPercentualAuto()))
                .percentual(req.getPercentual() == null ? 0 : req.getPercentual()).orcamentoId(req.getOrcamentoId()).build();
        // datas reais acompanham a situação, se ninguém informou
        if (situacao == Projetos.EM_ANDAMENTO && p.getInicioReal() == null) {
            p.setInicioReal(hoje);
        }
        if (situacao == Projetos.CONCLUIDO && p.getFimReal() == null) {
            p.setFimReal(hoje);
            if (p.getInicioReal() == null) {
                p.setInicioReal(p.getInicioPrevisto() != null && !p.getInicioPrevisto().isAfter(hoje) ? p.getInicioPrevisto() : hoje);
            }
        }
        if (Boolean.TRUE.equals(p.getPercentualAuto()) && atual != null) {
            p.setPercentual(percentualPelasTarefas(atual.getId()));
        } else if (Boolean.TRUE.equals(p.getPercentualAuto())) {
            p.setPercentual(0);
        }
        if (situacao == Projetos.CONCLUIDO) {
            p.setPercentual(100);
        }
        if (atual == null) {
            projetoMapper.insert(p);
        } else {
            projetoMapper.updateById(p);
            projetoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProjetoDO>()
                    .eq(ProjetoDO::getId, p.getId()).set(ProjetoDO::getCodigo, p.getCodigo())
                    .set(ProjetoDO::getResponsavelId, p.getResponsavelId())
                    .set(ProjetoDO::getInicioPrevisto, p.getInicioPrevisto()).set(ProjetoDO::getFimPrevisto, p.getFimPrevisto())
                    .set(ProjetoDO::getInicioReal, p.getInicioReal()).set(ProjetoDO::getFimReal, p.getFimReal())
                    .set(ProjetoDO::getOrcamentoId, p.getOrcamentoId()));
        }
        // equipe: troca pela lista enviada
        Set<Long> novos = new LinkedHashSet<>(req.getMembroIds());
        novos.remove(null);
        List<ProjetoMembroDO> existentes = projetoMembroMapper.selectList(ProjetoMembroDO::getProjetoId, p.getId());
        existentes.stream().filter(m -> !novos.contains(m.getUserId())).forEach(m -> projetoMembroMapper.deleteById(m.getId()));
        Set<Long> ja = existentes.stream().map(ProjetoMembroDO::getUserId).collect(Collectors.toSet());
        novos.stream().filter(u -> !ja.contains(u))
                .forEach(u -> projetoMembroMapper.insert(ProjetoMembroDO.builder().projetoId(p.getId()).userId(u).build()));
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        visivel(id);
        projetoTarefaMapper.delete(ProjetoTarefaDO::getProjetoId, id);
        projetoMembroMapper.delete(ProjetoMembroDO::getProjetoId, id);
        projetoVinculoMapper.delete(ProjetoVinculoDO::getProjetoId, id);
        custoService.apagarDe(CustoService.PROJETO, id);
        projetoMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ tarefas

    @Transactional(rollbackFor = Exception.class)
    public Long salvarTarefa(GovernancaVOs.Tarefa req) {
        ProjetoDO p = visivel(req.getProjetoId());
        exigirAberto(p);
        ProjetoTarefaDO atual = req.getId() == null ? null : tarefa(req.getId());
        if (atual != null && !atual.getProjetoId().equals(p.getId())) {
            throw exception(PROJETO_TAREFA_NAO_EXISTE);
        }
        validarPai(p.getId(), req.getId(), req.getTarefaPaiId());
        if (req.getInicioPrevisto() != null && req.getFimPrevisto() != null && req.getFimPrevisto().isBefore(req.getInicioPrevisto())) {
            throw exception(PROJETO_DATAS);
        }
        if (req.getResponsavelId() != null) {
            adminUserApi.validateUser(req.getResponsavelId());
        }
        int situacao = Projetos.situacaoDaTarefa(req.getSituacao() == null ? 1 : req.getSituacao(), req.getPercentual() == null ? 0 : req.getPercentual());
        ProjetoTarefaDO t = ProjetoTarefaDO.builder().id(req.getId()).projetoId(p.getId()).tarefaPaiId(req.getTarefaPaiId())
                .nome(req.getNome().trim()).descricao(HtmlSeguro.limpar(req.getDescricao())).responsavelId(req.getResponsavelId())
                .situacao(situacao).percentual(Projetos.percentualDaTarefa(situacao, req.getPercentual() == null ? 0 : req.getPercentual()))
                .inicioPrevisto(req.getInicioPrevisto()).fimPrevisto(req.getFimPrevisto())
                .inicioReal(req.getInicioReal()).fimReal(req.getFimReal())
                .horasPrevistas(nz(req.getHorasPrevistas())).horasGastas(nz(req.getHorasGastas()))
                .marco(Boolean.TRUE.equals(req.getMarco())).ordem(req.getOrdem() == null ? 0 : req.getOrdem()).build();
        datasReais(t);
        if (atual == null) {
            projetoTarefaMapper.insert(t);
        } else {
            projetoTarefaMapper.updateById(t);
            projetoTarefaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProjetoTarefaDO>()
                    .eq(ProjetoTarefaDO::getId, t.getId()).set(ProjetoTarefaDO::getTarefaPaiId, t.getTarefaPaiId())
                    .set(ProjetoTarefaDO::getResponsavelId, t.getResponsavelId())
                    .set(ProjetoTarefaDO::getInicioPrevisto, t.getInicioPrevisto()).set(ProjetoTarefaDO::getFimPrevisto, t.getFimPrevisto())
                    .set(ProjetoTarefaDO::getInicioReal, t.getInicioReal()).set(ProjetoTarefaDO::getFimReal, t.getFimReal()));
        }
        recalcular(p);
        return t.getId();
    }

    /** Andamento da tarefa: o responsável por ela ou quem gerencia o projeto. */
    @Transactional(rollbackFor = Exception.class)
    public void progresso(GovernancaVOs.Progresso req, Long userId) {
        ProjetoTarefaDO t = tarefa(req.getTarefaId());
        ProjetoDO p = visivel(t.getProjetoId());
        exigirAberto(p);
        if (!userId.equals(t.getResponsavelId()) && !permissionApi.hasAnyPermissions(userId, P_UPDATE)) {
            throw exception(PROJETO_TAREFA_SEM_PERMISSAO);
        }
        int situacao = Projetos.situacaoDaTarefa(req.getSituacao(), req.getPercentual());
        t.setSituacao(situacao);
        t.setPercentual(Projetos.percentualDaTarefa(situacao, req.getPercentual()));
        if (req.getHorasGastas() != null) {
            t.setHorasGastas(req.getHorasGastas());
        }
        datasReais(t);
        projetoTarefaMapper.updateById(t);
        recalcular(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirTarefa(Long id) {
        ProjetoTarefaDO t = tarefa(id);
        ProjetoDO p = visivel(t.getProjetoId());
        exigirAberto(p);
        // subtarefas sobem um nível
        projetoTarefaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProjetoTarefaDO>()
                .eq(ProjetoTarefaDO::getTarefaPaiId, id).set(ProjetoTarefaDO::getTarefaPaiId, t.getTarefaPaiId()));
        projetoTarefaMapper.deleteById(id);
        recalcular(p);
    }

    // ------------------------------------------------------------------ vínculos

    @Transactional(rollbackFor = Exception.class)
    public void vincular(GovernancaVOs.Vinculo req) {
        visivel(req.getProjetoId());
        boolean existe = switch (req.getObjeto()) {
            case "chamado" -> escopo.visivel(req.getObjetoId());
            case "problema" -> problemaMapper.selectById(req.getObjetoId()) != null;
            default -> mudancaMapper.selectById(req.getObjetoId()) != null;
        };
        if (!existe) {
            throw exception(switch (req.getObjeto()) {
                case "chamado" -> CHAMADO_NAO_EXISTE;
                case "problema" -> PROBLEMA_NAO_EXISTE;
                default -> MUDANCA_NAO_EXISTE;
            });
        }
        if (projetoVinculoMapper.selectCount(new LambdaQueryWrapperX<ProjetoVinculoDO>().eq(ProjetoVinculoDO::getProjetoId, req.getProjetoId())
                .eq(ProjetoVinculoDO::getObjeto, req.getObjeto()).eq(ProjetoVinculoDO::getObjetoId, req.getObjetoId())) > 0) {
            throw exception(PROJETO_VINCULO_DUPLICADO);
        }
        projetoVinculoMapper.insert(ProjetoVinculoDO.builder().projetoId(req.getProjetoId()).objeto(req.getObjeto())
                .objetoId(req.getObjetoId()).build());
    }

    @Transactional(rollbackFor = Exception.class)
    public void desvincular(Long vinculoId) {
        ProjetoVinculoDO v = vinculoId == null ? null : projetoVinculoMapper.selectById(vinculoId);
        if (v == null) {
            throw exception(PROJETO_VINCULO_NAO_EXISTE);
        }
        visivel(v.getProjetoId());
        projetoVinculoMapper.deleteById(vinculoId);
    }

    // ------------------------------------------------------------------ internos

    private void recalcular(ProjetoDO p) {
        if (Boolean.TRUE.equals(p.getPercentualAuto())) {
            projetoMapper.updateById(ProjetoDO.builder().id(p.getId()).percentual(percentualPelasTarefas(p.getId())).build());
        }
    }

    private int percentualPelasTarefas(Long projetoId) {
        return Projetos.percentual(projetoTarefaMapper.selectList(ProjetoTarefaDO::getProjetoId, projetoId).stream()
                .map(t -> new Projetos.Tarefa(t.getSituacao(), t.getPercentual(), t.getHorasPrevistas())).toList());
    }

    /** Começou: data de início real; concluiu: data de fim real (se ninguém informou). */
    private static void datasReais(ProjetoTarefaDO t) {
        LocalDate hoje = LocalDate.now();
        if (t.getSituacao() >= Projetos.TAREFA_EM_ANDAMENTO && t.getSituacao() != Projetos.TAREFA_CANCELADA && t.getInicioReal() == null) {
            t.setInicioReal(hoje);
        }
        if (t.getSituacao() == Projetos.TAREFA_CONCLUIDA && t.getFimReal() == null) {
            t.setFimReal(hoje);
        }
    }

    /** A tarefa superior é do mesmo projeto e não é a própria tarefa nem uma descendente dela (sem ciclo). */
    private void validarPai(Long projetoId, Long tarefaId, Long paiId) {
        if (paiId == null) {
            return;
        }
        Map<Long, ProjetoTarefaDO> todas = projetoTarefaMapper.selectList(ProjetoTarefaDO::getProjetoId, projetoId).stream()
                .collect(Collectors.toMap(ProjetoTarefaDO::getId, x -> x));
        Long atual = paiId;
        int passos = 0;
        while (atual != null) {
            if (!todas.containsKey(atual) || atual.equals(tarefaId) || ++passos > 1000) {
                throw exception(PROJETO_TAREFA_PAI_INVALIDA);
            }
            atual = todas.get(atual).getTarefaPaiId();
        }
    }

    private static void exigirAberto(ProjetoDO p) {
        if (Projetos.encerrado(p.getSituacao())) {
            throw exception(PROJETO_ENCERRADO);
        }
    }

    private ProjetoTarefaDO tarefa(Long id) {
        ProjetoTarefaDO t = id == null ? null : projetoTarefaMapper.selectById(id);
        if (t == null) {
            throw exception(PROJETO_TAREFA_NAO_EXISTE);
        }
        return t;
    }

    ProjetoDO visivel(Long id) {
        ProjetoDO p = id == null ? null : projetoMapper.selectById(id);
        if (p == null) {
            throw exception(PROJETO_NAO_EXISTE);
        }
        return p;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** userId preenchido = detalhe (tarefas, equipe, vínculos e ações). */
    private List<GovernancaVOs.Projeto> converter(List<ProjetoDO> lista, Long userId) {
        LocalDate hoje = LocalDate.now();
        Set<Long> ids = lista.stream().map(ProjetoDO::getId).collect(Collectors.toSet());
        List<ProjetoTarefaDO> tarefas = ids.isEmpty() ? List.of() : projetoTarefaMapper.selectList(new LambdaQueryWrapperX<ProjetoTarefaDO>()
                .in(ProjetoTarefaDO::getProjetoId, ids).orderByAsc(ProjetoTarefaDO::getOrdem)
                .orderByAsc(ProjetoTarefaDO::getInicioPrevisto).orderByAsc(ProjetoTarefaDO::getId));
        Map<Long, List<ProjetoTarefaDO>> porProjeto = tarefas.stream().collect(Collectors.groupingBy(ProjetoTarefaDO::getProjetoId));
        Map<Long, BigDecimal> custos = custoService.totais(CustoService.PROJETO, ids);
        Map<Long, String> orcamentos = orcamentoService.nomes(lista.stream().map(ProjetoDO::getOrcamentoId).toList());
        List<ProjetoMembroDO> membros = userId == null || ids.isEmpty() ? List.of()
                : projetoMembroMapper.selectList(new LambdaQueryWrapperX<ProjetoMembroDO>().in(ProjetoMembroDO::getProjetoId, ids));
        Set<Long> pessoas = new HashSet<>();
        lista.forEach(p -> pessoas.add(p.getResponsavelId()));
        tarefas.forEach(t -> pessoas.add(t.getResponsavelId()));
        membros.forEach(m -> pessoas.add(m.getUserId()));
        pessoas.remove(null);
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        boolean gerencia = userId != null && permissionApi.hasAnyPermissions(userId, P_UPDATE);
        return lista.stream().map(p -> {
            GovernancaVOs.Projeto v = new GovernancaVOs.Projeto();
            v.setId(p.getId());
            v.setNome(p.getNome());
            v.setCodigo(p.getCodigo());
            v.setSituacao(p.getSituacao());
            v.setSituacaoRotulo(Projetos.rotulo(p.getSituacao()));
            v.setPrioridade(p.getPrioridade());
            v.setDeptId(p.getDeptId());
            v.setDept(apoio.nomeDept(p.getDeptId()));
            v.setResponsavelId(p.getResponsavelId());
            v.setResponsavel(nome(nomes, p.getResponsavelId()));
            v.setInicioPrevisto(p.getInicioPrevisto());
            v.setFimPrevisto(p.getFimPrevisto());
            v.setInicioReal(p.getInicioReal());
            v.setFimReal(p.getFimReal());
            v.setPercentual(p.getPercentual());
            v.setPercentualAuto(p.getPercentualAuto());
            v.setOrcamentoId(p.getOrcamentoId());
            v.setOrcamento(p.getOrcamentoId() == null ? null : orcamentos.getOrDefault(p.getOrcamentoId(), "#" + p.getOrcamentoId()));
            v.setAtrasado(!Projetos.encerrado(p.getSituacao()) && p.getFimPrevisto() != null && p.getFimPrevisto().isBefore(hoje));
            List<ProjetoTarefaDO> ts = porProjeto.getOrDefault(p.getId(), List.of());
            v.setTotalTarefas((int) ts.stream().filter(t -> t.getSituacao() != Projetos.TAREFA_CANCELADA).count());
            v.setTarefasConcluidas((int) ts.stream().filter(t -> t.getSituacao() == Projetos.TAREFA_CONCLUIDA).count());
            v.setHorasPrevistas(ts.stream().map(ProjetoTarefaDO::getHorasPrevistas).reduce(BigDecimal.ZERO, BigDecimal::add));
            v.setHorasGastas(ts.stream().map(ProjetoTarefaDO::getHorasGastas).reduce(BigDecimal.ZERO, BigDecimal::add));
            v.setCustoTotal(custos.getOrDefault(p.getId(), BigDecimal.ZERO));
            if (userId != null) {
                v.setDescricao(p.getDescricao());
                boolean aberto = !Projetos.encerrado(p.getSituacao());
                v.setTarefas(ts.stream().map(t -> tarefaVO(t, nomes, hoje, aberto && (gerencia || userId.equals(t.getResponsavelId())))).toList());
                v.setMembros(membros.stream().filter(m -> m.getProjetoId().equals(p.getId())).map(m -> {
                    GovernancaVOs.Membro x = new GovernancaVOs.Membro();
                    x.setUserId(m.getUserId());
                    x.setNome(nome(nomes, m.getUserId()));
                    x.setPapel(m.getPapel());
                    return x;
                }).toList());
                v.setMembroIds(v.getMembros().stream().map(GovernancaVOs.Membro::getUserId).toList());
                v.setVinculos(vinculos(p.getId()));
                List<String> acoes = new ArrayList<>();
                if (gerencia) {
                    acoes.addAll(List.of("editar", "excluir", "vincular", "custos"));
                    if (aberto) {
                        acoes.add("tarefas");
                    }
                }
                v.setAcoes(acoes);
            }
            return v;
        }).toList();
    }

    private static GovernancaVOs.Tarefa tarefaVO(ProjetoTarefaDO t, Map<Long, AdminUserRespDTO> nomes, LocalDate hoje, boolean podeEditar) {
        GovernancaVOs.Tarefa x = new GovernancaVOs.Tarefa();
        x.setId(t.getId());
        x.setProjetoId(t.getProjetoId());
        x.setTarefaPaiId(t.getTarefaPaiId());
        x.setNome(t.getNome());
        x.setDescricao(t.getDescricao());
        x.setResponsavelId(t.getResponsavelId());
        x.setResponsavel(nome(nomes, t.getResponsavelId()));
        x.setSituacao(t.getSituacao());
        x.setSituacaoRotulo(Projetos.rotuloTarefa(t.getSituacao()));
        x.setInicioPrevisto(t.getInicioPrevisto());
        x.setFimPrevisto(t.getFimPrevisto());
        x.setInicioReal(t.getInicioReal());
        x.setFimReal(t.getFimReal());
        x.setHorasPrevistas(t.getHorasPrevistas());
        x.setHorasGastas(t.getHorasGastas());
        x.setPercentual(t.getPercentual());
        x.setMarco(t.getMarco());
        x.setOrdem(t.getOrdem());
        x.setAtrasada(t.getSituacao() < Projetos.TAREFA_CONCLUIDA && t.getFimPrevisto() != null && t.getFimPrevisto().isBefore(hoje));
        x.setPodeEditar(podeEditar);
        return x;
    }

    private List<GovernancaVOs.Vinculo> vinculos(Long projetoId) {
        List<ProjetoVinculoDO> lista = projetoVinculoMapper.selectList(new LambdaQueryWrapperX<ProjetoVinculoDO>()
                .eq(ProjetoVinculoDO::getProjetoId, projetoId).orderByAsc(ProjetoVinculoDO::getObjeto).orderByAsc(ProjetoVinculoDO::getObjetoId));
        return lista.stream().map(v -> {
            GovernancaVOs.Vinculo x = new GovernancaVOs.Vinculo();
            x.setId(v.getId());
            x.setProjetoId(projetoId);
            x.setObjeto(v.getObjeto());
            x.setObjetoId(v.getObjetoId());
            // fora do escopo de unidade de quem consulta: fica só o número
            switch (v.getObjeto()) {
                case "chamado" -> Optional.ofNullable(chamadoMapper.selectById(v.getObjetoId())).ifPresent(c -> {
                    x.setTitulo(c.getTitulo());
                    x.setStatusRotulo(ChamadoStatus.de(c.getStatus()).getRotulo());
                });
                case "problema" -> Optional.ofNullable(problemaMapper.selectById(v.getObjetoId())).ifPresent(c -> {
                    x.setTitulo(c.getTitulo());
                    x.setStatusRotulo(ProblemaStatus.de(c.getStatus()).getRotulo());
                });
                default -> Optional.ofNullable(mudancaMapper.selectById(v.getObjetoId())).ifPresent(c -> {
                    x.setTitulo(c.getTitulo());
                    x.setStatusRotulo(MudancaStatus.de(c.getStatus()).getRotulo());
                });
            }
            return x;
        }).toList();
    }

    private static String nome(Map<Long, AdminUserRespDTO> m, Long id) {
        return id == null ? null : Optional.ofNullable(m.get(id)).map(AdminUserRespDTO::getNickname).orElse("#" + id);
    }

}
