package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.SlaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dominio.Calendario;
import cn.iocoder.yudao.module.itsm.dominio.DefinicaoSla;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Calendários, feriados, SLAs e o cálculo dos prazos dos chamados.
 *
 * <p>Escolha do SLA (na abertura e quando muda categoria ou prioridade): o da categoria; se ela não define,
 * o padrão da prioridade. Prazos contam a partir da abertura; o tempo em pendente dentro do expediente
 * empurra o prazo de solução (como no GLPI). O prazo de atendimento não muda depois que alguém assumiu.
 */
@Service
public class SlaService {

    public static final int TIPO_SOLUCAO = 0;
    public static final int TIPO_ATENDIMENTO = 1;

    @Resource
    private CalendarioMapper calendarioMapper;
    @Resource
    private CalendarioPeriodoMapper periodoMapper;
    @Resource
    private FeriadoMapper feriadoMapper;
    @Resource
    private CalendarioFeriadoMapper calendarioFeriadoMapper;
    @Resource
    private SlaMapper slaMapper;
    @Resource
    private SlaNivelMapper nivelMapper;
    @Resource
    private SlaPrioridadeMapper porPrioridadeMapper;
    @Resource
    private CategoriaMapper categoriaMapper;
    @Resource
    private ChamadoMapper chamadoMapper;

    // ======================================================================= prazos dos chamados

    /** Define SLAs e prazos de um chamado recém-aberto (ainda não gravado). */
    public void aplicarNaAbertura(ChamadoDO c) {
        Long[] ids = escolher(c.getCategoriaId(), c.getPrioridade());
        c.setSlaAtendimentoId(ids[0]);
        c.setSlaSolucaoId(ids[1]);
        c.setPrazoAtendimento(prazo(ids[0], c.getAbertoEm(), 0));
        c.setPrazoSolucao(prazo(ids[1], c.getAbertoEm(), 0));
    }

    /**
     * Categoria ou prioridade mudou: escolhe de novo e recalcula, só para chamado em aberto.
     *
     * @return true se algum SLA ou prazo mudou
     */
    public boolean recalcular(ChamadoDO c) {
        if (!ChamadoStatus.de(c.getStatus()).aberto()) {
            return false;
        }
        Long[] ids = escolher(c.getCategoriaId(), c.getPrioridade());
        boolean mudou = false;
        if (c.getAssumidoEm() == null && !Objects.equals(ids[0], c.getSlaAtendimentoId())) {
            c.setSlaAtendimentoId(ids[0]);
            c.setPrazoAtendimento(prazo(ids[0], c.getAbertoEm(), 0));
            mudou = true;
        }
        if (!Objects.equals(ids[1], c.getSlaSolucaoId())) {
            c.setSlaSolucaoId(ids[1]);
            c.setPrazoSolucao(prazo(ids[1], c.getAbertoEm(), nz(c.getEsperaSlaSeg())));
            mudou = true;
        }
        return mudou;
    }

    /** Saiu de pendente: soma o tempo parado no expediente do SLA e empurra o prazo de solução. */
    public void aposPendencia(ChamadoDO c, LocalDateTime inicioEspera, LocalDateTime agora) {
        if (c.getSlaSolucaoId() == null || inicioEspera == null) {
            return;
        }
        DefinicaoSla def = definicao(c.getSlaSolucaoId());
        if (def == null) {
            return;
        }
        c.setEsperaSlaSeg(nz(c.getEsperaSlaSeg()) + def.ativoEntre(inicioEspera, agora));
        c.setPrazoSolucao(def.prazo(c.getAbertoEm(), c.getEsperaSlaSeg()));
    }

    /** [atendimento, solução] */
    Long[] escolher(Long categoriaId, Integer prioridade) {
        Long tto = null;
        Long ttr = null;
        if (categoriaId != null) {
            CategoriaDO cat = categoriaMapper.selectById(categoriaId);
            if (cat != null) {
                tto = ativo(cat.getSlaAtendimentoId());
                ttr = ativo(cat.getSlaSolucaoId());
            }
        }
        if ((tto == null || ttr == null) && prioridade != null) {
            SlaPrioridadeDO p = porPrioridadeMapper.selectById(prioridade);
            if (p != null) {
                tto = tto != null ? tto : ativo(p.getSlaAtendimentoId());
                ttr = ttr != null ? ttr : ativo(p.getSlaSolucaoId());
            }
        }
        return new Long[]{tto, ttr};
    }

    private Long ativo(Long slaId) {
        if (slaId == null) {
            return null;
        }
        SlaDO s = slaMapper.selectById(slaId);
        return s != null && Boolean.TRUE.equals(s.getAtivo()) ? slaId : null;
    }

    LocalDateTime prazo(Long slaId, LocalDateTime inicio, long espera) {
        DefinicaoSla def = slaId == null ? null : definicao(slaId);
        return def == null ? null : def.prazo(inicio, espera);
    }

    public DefinicaoSla definicao(Long slaId) {
        SlaDO s = slaMapper.selectById(slaId);
        if (s == null) {
            return null;
        }
        return new DefinicaoSla(s.getTempo(), s.getUnidade(),
                s.getCalendarioId() == null ? null : calendario(s.getCalendarioId()), Boolean.TRUE.equals(s.getFimExpediente()));
    }

    /** Monta o calendário de domínio (períodos e feriados) a partir do banco. */
    public Calendario calendario(Long id) {
        if (calendarioMapper.selectById(id) == null) {
            return null;
        }
        Map<DayOfWeek, List<Calendario.Periodo>> p = new EnumMap<>(DayOfWeek.class);
        for (CalendarioPeriodoDO x : periodoMapper.selectList(CalendarioPeriodoDO::getCalendarioId, id)) {
            p.computeIfAbsent(DayOfWeek.of(x.getDiaSemana()), k -> new ArrayList<>()).add(Calendario.Periodo.de(x.getInicio(), x.getFim()));
        }
        List<Long> feriadoIds = calendarioFeriadoMapper.selectList(CalendarioFeriadoDO::getCalendarioId, id).stream()
                .map(CalendarioFeriadoDO::getFeriadoId).toList();
        List<Calendario.Feriado> f = feriadoIds.isEmpty() ? List.of() : feriadoMapper.selectByIds(feriadoIds).stream()
                .map(x -> new Calendario.Feriado(x.getDataInicio(), x.getDataFim() == null ? x.getDataInicio() : x.getDataFim(),
                        Boolean.TRUE.equals(x.getPerpetuo())))
                .toList();
        return new Calendario(p, f);
    }

    public List<SlaNivelDO> niveisAtivos(Long slaId) {
        return nivelMapper.selectList(new LambdaQueryWrapperX<SlaNivelDO>().eq(SlaNivelDO::getSlaId, slaId)
                .eq(SlaNivelDO::getAtivo, true).orderByAsc(SlaNivelDO::getTempoSeg));
    }

    public SlaDO obterSla(Long id) {
        SlaDO s = id == null ? null : slaMapper.selectById(id);
        if (s == null) {
            throw exception(SLA_NAO_EXISTE);
        }
        return s;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    // ======================================================================= calendários

    public List<SlaVOs.Calendario> listarCalendarios() {
        List<CalendarioDO> cals = calendarioMapper.selectList(new LambdaQueryWrapperX<CalendarioDO>().orderByAsc(CalendarioDO::getNome));
        Map<Long, List<CalendarioPeriodoDO>> periodos = periodoMapper.selectList().stream()
                .collect(Collectors.groupingBy(CalendarioPeriodoDO::getCalendarioId));
        Map<Long, List<Long>> feriados = calendarioFeriadoMapper.selectList().stream()
                .collect(Collectors.groupingBy(CalendarioFeriadoDO::getCalendarioId,
                        Collectors.mapping(CalendarioFeriadoDO::getFeriadoId, Collectors.toList())));
        return cals.stream().map(c -> {
            SlaVOs.Calendario v = new SlaVOs.Calendario();
            v.setId(c.getId());
            v.setNome(c.getNome());
            v.setDescricao(c.getDescricao());
            v.setPeriodos(periodos.getOrDefault(c.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(CalendarioPeriodoDO::getDiaSemana).thenComparing(CalendarioPeriodoDO::getInicio))
                    .map(p -> {
                        SlaVOs.Periodo x = new SlaVOs.Periodo();
                        x.setDiaSemana(p.getDiaSemana());
                        x.setInicio(p.getInicio().toString().substring(0, 5));
                        x.setFim(p.getFim().equals(LocalTime.MIDNIGHT) ? "24:00" : p.getFim().toString().substring(0, 5));
                        return x;
                    }).toList());
            v.setFeriadoIds(feriados.getOrDefault(c.getId(), List.of()));
            return v;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarCalendario(SlaVOs.Calendario req) {
        if (req.getId() != null && calendarioMapper.selectById(req.getId()) == null) {
            throw exception(CALENDARIO_NAO_EXISTE);
        }
        // valida os períodos antes de gravar (sobreposição no mesmo dia também é erro)
        Map<Integer, List<Calendario.Periodo>> porDia = new HashMap<>();
        for (SlaVOs.Periodo p : req.getPeriodos()) {
            Calendario.Periodo x;
            try {
                x = Calendario.Periodo.de(LocalTime.parse(p.getInicio()), hora(p.getFim()));
            } catch (IllegalArgumentException e) {
                throw exception(CALENDARIO_PERIODO_INVALIDO);
            }
            for (Calendario.Periodo o : porDia.computeIfAbsent(p.getDiaSemana(), k -> new ArrayList<>())) {
                if (x.inicio() < o.fim() && o.inicio() < x.fim()) {
                    throw exception(CALENDARIO_PERIODO_INVALIDO);
                }
            }
            porDia.get(p.getDiaSemana()).add(x);
        }
        CalendarioDO c = CalendarioDO.builder().id(req.getId()).nome(req.getNome().trim()).descricao(req.getDescricao()).build();
        if (c.getId() == null) {
            calendarioMapper.insert(c);
        } else {
            calendarioMapper.updateById(c);
            periodoMapper.delete(CalendarioPeriodoDO::getCalendarioId, c.getId());
            calendarioFeriadoMapper.delete(CalendarioFeriadoDO::getCalendarioId, c.getId());
        }
        for (SlaVOs.Periodo p : req.getPeriodos()) {
            periodoMapper.insert(CalendarioPeriodoDO.builder().calendarioId(c.getId()).diaSemana(p.getDiaSemana())
                    .inicio(LocalTime.parse(p.getInicio())).fim(hora(p.getFim())).build());
        }
        for (Long f : new LinkedHashSet<>(req.getFeriadoIds())) {
            if (feriadoMapper.selectById(f) == null) {
                throw exception(FERIADO_NAO_EXISTE);
            }
            calendarioFeriadoMapper.insert(CalendarioFeriadoDO.builder().calendarioId(c.getId()).feriadoId(f).build());
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirCalendario(Long id) {
        if (calendarioMapper.selectById(id) == null) {
            throw exception(CALENDARIO_NAO_EXISTE);
        }
        if (slaMapper.selectCount(SlaDO::getCalendarioId, id) > 0) {
            throw exception(CALENDARIO_EM_USO);
        }
        periodoMapper.delete(CalendarioPeriodoDO::getCalendarioId, id);
        calendarioFeriadoMapper.delete(CalendarioFeriadoDO::getCalendarioId, id);
        calendarioMapper.deleteById(id);
    }

    private static LocalTime hora(String hhmm) {
        return "24:00".equals(hhmm) ? LocalTime.MIDNIGHT : LocalTime.parse(hhmm);
    }

    // ======================================================================= feriados

    public List<FeriadoDO> listarFeriados() {
        return feriadoMapper.selectList(new LambdaQueryWrapperX<FeriadoDO>().orderByAsc(FeriadoDO::getDataInicio));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarFeriado(SlaVOs.Feriado req) {
        if (req.getId() != null && feriadoMapper.selectById(req.getId()) == null) {
            throw exception(FERIADO_NAO_EXISTE);
        }
        FeriadoDO f = FeriadoDO.builder().id(req.getId()).nome(req.getNome().trim()).dataInicio(req.getDataInicio())
                .dataFim(req.getDataFim() == null ? req.getDataInicio() : req.getDataFim())
                .perpetuo(Boolean.TRUE.equals(req.getPerpetuo())).build();
        if (f.getDataFim().isBefore(f.getDataInicio()) && !f.getPerpetuo()) {
            throw exception(FERIADO_DATAS_INVALIDAS);
        }
        if (f.getId() == null) {
            feriadoMapper.insert(f);
        } else {
            feriadoMapper.updateById(f);
        }
        return f.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirFeriado(Long id) {
        calendarioFeriadoMapper.delete(CalendarioFeriadoDO::getFeriadoId, id);
        feriadoMapper.deleteById(id);
    }

    // ======================================================================= SLAs

    public List<SlaVOs.Sla> listarSlas() {
        Map<Long, List<SlaNivelDO>> niveis = nivelMapper.selectList().stream().collect(Collectors.groupingBy(SlaNivelDO::getSlaId));
        return slaMapper.selectList(new LambdaQueryWrapperX<SlaDO>().orderByAsc(SlaDO::getTipo).orderByAsc(SlaDO::getNome)).stream()
                .map(s -> {
                    SlaVOs.Sla v = new SlaVOs.Sla();
                    v.setId(s.getId());
                    v.setNome(s.getNome());
                    v.setTipo(s.getTipo());
                    v.setTempo(s.getTempo());
                    v.setUnidade(s.getUnidade());
                    v.setCalendarioId(s.getCalendarioId());
                    v.setFimExpediente(s.getFimExpediente());
                    v.setDescricao(s.getDescricao());
                    v.setAtivo(s.getAtivo());
                    v.setNiveis(niveis.getOrDefault(s.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(SlaNivelDO::getTempoSeg)).map(n -> {
                                SlaVOs.Nivel x = new SlaVOs.Nivel();
                                x.setId(n.getId());
                                x.setNome(n.getNome());
                                x.setMinutos(n.getTempoSeg() / 60);
                                x.setAtribuirEquipeId(n.getAtribuirEquipeId());
                                x.setAtribuirTecnicoId(n.getAtribuirTecnicoId());
                                x.setNovaPrioridade(n.getNovaPrioridade());
                                x.setAtivo(n.getAtivo());
                                return x;
                            }).toList());
                    return v;
                }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarSla(SlaVOs.Sla req) {
        if (req.getId() != null) {
            obterSla(req.getId());
        }
        if (req.getCalendarioId() != null && calendarioMapper.selectById(req.getCalendarioId()) == null) {
            throw exception(CALENDARIO_NAO_EXISTE);
        }
        try {
            new DefinicaoSla(req.getTempo(), req.getUnidade(), null, Boolean.TRUE.equals(req.getFimExpediente()));
        } catch (IllegalArgumentException e) {
            throw exception(SLA_INVALIDO, e.getMessage());
        }
        SlaDO s = SlaDO.builder().id(req.getId()).nome(req.getNome().trim()).tipo(req.getTipo()).tempo(req.getTempo())
                .unidade(req.getUnidade()).calendarioId(req.getCalendarioId())
                .fimExpediente(Boolean.TRUE.equals(req.getFimExpediente())).descricao(req.getDescricao())
                .ativo(!Boolean.FALSE.equals(req.getAtivo())).build();
        if (s.getId() == null) {
            slaMapper.insert(s);
        } else {
            slaMapper.updateById(s);
            // calendário pode ter sido removido (24x7): grava o vazio explicitamente
            if (req.getCalendarioId() == null) {
                slaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SlaDO>()
                        .eq(SlaDO::getId, s.getId()).set(SlaDO::getCalendarioId, null));
            }
        }
        // níveis: substitui a lista; execuções já feitas de níveis removidos ficam no histórico
        Set<Long> mantidos = new HashSet<>();
        for (SlaVOs.Nivel n : req.getNiveis()) {
            SlaNivelDO x = SlaNivelDO.builder().id(n.getId()).slaId(s.getId()).nome(n.getNome().trim())
                    .tempoSeg(n.getMinutos() * 60).atribuirEquipeId(n.getAtribuirEquipeId())
                    .atribuirTecnicoId(n.getAtribuirTecnicoId()).novaPrioridade(n.getNovaPrioridade())
                    .ativo(!Boolean.FALSE.equals(n.getAtivo())).build();
            SlaNivelDO existente = x.getId() == null ? null : nivelMapper.selectById(x.getId());
            if (existente == null || !existente.getSlaId().equals(s.getId())) {
                x.setId(null);
                nivelMapper.insert(x);
            } else {
                nivelMapper.updateById(x);
            }
            mantidos.add(x.getId());
        }
        nivelMapper.selectList(SlaNivelDO::getSlaId, s.getId()).stream().filter(n -> !mantidos.contains(n.getId()))
                .forEach(n -> nivelMapper.deleteById(n.getId()));
        return s.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirSla(Long id) {
        obterSla(id);
        boolean emUso = categoriaMapper.selectCount(new LambdaQueryWrapperX<CategoriaDO>()
                .and(w -> w.eq(CategoriaDO::getSlaAtendimentoId, id).or().eq(CategoriaDO::getSlaSolucaoId, id))) > 0
                || porPrioridadeMapper.selectCount(new LambdaQueryWrapperX<SlaPrioridadeDO>()
                .and(w -> w.eq(SlaPrioridadeDO::getSlaAtendimentoId, id).or().eq(SlaPrioridadeDO::getSlaSolucaoId, id))) > 0
                || chamadoMapper.selectCount(new LambdaQueryWrapperX<ChamadoDO>()
                .lt(ChamadoDO::getStatus, ChamadoStatus.SOLUCIONADO.getCodigo())
                .and(w -> w.eq(ChamadoDO::getSlaAtendimentoId, id).or().eq(ChamadoDO::getSlaSolucaoId, id))) > 0;
        if (emUso) {
            throw exception(SLA_EM_USO);
        }
        nivelMapper.delete(SlaNivelDO::getSlaId, id);
        slaMapper.deleteById(id);
    }

    public List<SlaPrioridadeDO> listarPorPrioridade() {
        return porPrioridadeMapper.selectList(new LambdaQueryWrapperX<SlaPrioridadeDO>().orderByAsc(SlaPrioridadeDO::getPrioridade));
    }

    @Transactional(rollbackFor = Exception.class)
    public void salvarPorPrioridade(List<SlaVOs.PorPrioridade> lista) {
        for (SlaVOs.PorPrioridade p : lista) {
            validarTipo(p.getSlaAtendimentoId(), TIPO_ATENDIMENTO);
            validarTipo(p.getSlaSolucaoId(), TIPO_SOLUCAO);
            porPrioridadeMapper.updateById(SlaPrioridadeDO.builder().prioridade(p.getPrioridade())
                    .slaAtendimentoId(p.getSlaAtendimentoId()).slaSolucaoId(p.getSlaSolucaoId()).build());
        }
    }

    /** O SLA escolhido tem que ser do tipo certo (atendimento num campo, solução no outro). */
    public void validarTipo(Long slaId, int tipo) {
        if (slaId != null && obterSla(slaId).getTipo() != tipo) {
            throw exception(SLA_TIPO_ERRADO);
        }
    }

}
