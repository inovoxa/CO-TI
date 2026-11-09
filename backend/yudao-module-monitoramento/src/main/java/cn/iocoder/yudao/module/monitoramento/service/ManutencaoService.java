package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.ManutencaoDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.ManutencaoMonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonManutencaoMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonManutencaoMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.dominio.Regras;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.*;

/**
 * Janelas de manutenção: enquanto valem, os monitores delas ficam "em manutenção" (sem verificar, sem aviso e sem
 * chamado), como no Kuma.
 */
@Service
public class ManutencaoService {

    /** Quanto tempo o conjunto "quem está em manutenção agora" vale antes de recalcular */
    private static final long CACHE_MS = 5_000;

    @Resource
    private MonManutencaoMapper monManutencaoMapper;
    @Resource
    private MonManutencaoMonitorMapper monManutencaoMonitorMapper;
    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private MonitorUnitMapper monitorUnitMapper;

    private volatile Set<Long> emManutencao = Set.of();
    private volatile long calculadoEm;

    public List<Planilha.Aba> planilha() {
        List<String> dias = List.of("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom");
        List<List<Object>> linhas = listar().stream().<List<Object>>map(m -> Arrays.asList(m.getTitulo(), m.getDescricao(), m.getTipo(),
                Planilha.simNao(m.getAtiva()), Planilha.simNao(m.getValendo()), Planilha.dataHora(m.getInicio()),
                Planilha.dataHora(m.getFim()), m.getDiasSemana() == null ? null
                        : String.join(", ", m.getDiasSemana().stream().map(d -> dias.get(d - 1)).toList()),
                m.getHoraInicio() == null ? null : m.getHoraInicio().toString(),
                m.getHoraFim() == null ? null : m.getHoraFim().toString(), m.getUnidadeNome(),
                m.getMonitorNomes() == null ? null : String.join(", ", m.getMonitorNomes()))).toList();
        return List.of(new Planilha.Aba("Manutenções", List.of("Título", "Descrição", "Tipo", "Ligada", "Valendo agora", "Início", "Fim",
                "Dias da semana", "Hora de início", "Hora de fim", "Unidade", "Monitores"), linhas));
    }

    public List<MonitorVOs.Manutencao> listar() {
        List<ManutencaoDO> lista = monManutencaoMapper.selectList(new LambdaQueryWrapperX<ManutencaoDO>().orderByDesc(ManutencaoDO::getId));
        if (lista.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<Long>> monitores = monitoresDe(lista.stream().map(ManutencaoDO::getId).toList());
        Set<Long> todosMonitores = monitores.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, String> nomes = todosMonitores.isEmpty() ? Map.of() : monMonitorMapper.selectByIds(todosMonitores).stream()
                .collect(Collectors.toMap(MonitorDO::getId, MonitorDO::getNome));
        Map<Long, String> unidades = monitorUnitMapper.selectList().stream().collect(Collectors.toMap(MonitorUnitDO::getId, MonitorUnitDO::getName));
        LocalDateTime agora = LocalDateTime.now();
        return lista.stream().map(m -> {
            MonitorVOs.Manutencao vo = converter(m);
            vo.setMonitorIds(monitores.getOrDefault(m.getId(), List.of()));
            vo.setMonitorNomes(vo.getMonitorIds().stream().map(nomes::get).filter(Objects::nonNull).toList());
            vo.setUnidadeNome(m.getUnidadeId() == null ? null : unidades.get(m.getUnidadeId()));
            vo.setValendo(valendo(m, agora));
            return vo;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(MonitorVOs.Manutencao req) {
        validar(req);
        ManutencaoDO m = ManutencaoDO.builder().id(req.getId()).titulo(req.getTitulo().trim()).descricao(req.getDescricao()).tipo(req.getTipo())
                .ativa(req.getAtiva() == null || req.getAtiva()).inicio(req.getInicio()).fim(req.getFim())
                .diasSemana(req.getDiasSemana() == null ? null : req.getDiasSemana().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")))
                .horaInicio(req.getHoraInicio()).horaFim(req.getHoraFim()).unidadeId(req.getUnidadeId()).build();
        if (req.getId() == null) {
            monManutencaoMapper.insert(m);
        } else {
            if (monManutencaoMapper.selectById(req.getId()) == null) {
                throw exception(MANUTENCAO_NAO_EXISTE);
            }
            monManutencaoMapper.updateById(m);
            // campos que podem ficar vazios
            monManutencaoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ManutencaoDO>()
                    .eq(ManutencaoDO::getId, m.getId()).set(ManutencaoDO::getUnidadeId, m.getUnidadeId())
                    .set(ManutencaoDO::getInicio, m.getInicio()).set(ManutencaoDO::getFim, m.getFim())
                    .set(ManutencaoDO::getDiasSemana, m.getDiasSemana()).set(ManutencaoDO::getHoraInicio, m.getHoraInicio())
                    .set(ManutencaoDO::getHoraFim, m.getHoraFim()).set(ManutencaoDO::getDescricao, m.getDescricao()));
            monManutencaoMonitorMapper.delete(ManutencaoMonitorDO::getManutencaoId, m.getId());
        }
        for (Long monitorId : new LinkedHashSet<>(Optional.ofNullable(req.getMonitorIds()).orElse(List.of()))) {
            monManutencaoMonitorMapper.insert(ManutencaoMonitorDO.builder().manutencaoId(m.getId()).monitorId(monitorId).build());
        }
        calculadoEm = 0;
        return m.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        monManutencaoMapper.deleteById(id);
        monManutencaoMonitorMapper.delete(ManutencaoMonitorDO::getManutencaoId, id);
        calculadoEm = 0;
    }

    /** Liga ou desliga (a manual vale enquanto ligada). */
    public void ativar(Long id, boolean ativa) {
        if (monManutencaoMapper.selectById(id) == null) {
            throw exception(MANUTENCAO_NAO_EXISTE);
        }
        monManutencaoMapper.updateById(ManutencaoDO.builder().id(id).ativa(ativa).build());
        calculadoEm = 0;
    }

    /** Monitores em manutenção agora (pelas janelas que valem), em cache de poucos segundos. */
    @DataPermission(enable = false)
    public Set<Long> monitoresEmManutencao() {
        if (System.currentTimeMillis() - calculadoEm < CACHE_MS) {
            return emManutencao;
        }
        LocalDateTime agora = LocalDateTime.now();
        List<ManutencaoDO> valendo = monManutencaoMapper.selectList(new LambdaQueryWrapperX<ManutencaoDO>().eq(ManutencaoDO::getAtiva, true))
                .stream().filter(m -> valendo(m, agora)).toList();
        Set<Long> ids = new HashSet<>();
        if (!valendo.isEmpty()) {
            monitoresDe(valendo.stream().map(ManutencaoDO::getId).toList()).values().forEach(ids::addAll);
            Set<Long> unidades = valendo.stream().map(ManutencaoDO::getUnidadeId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!unidades.isEmpty()) {
                monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().in(MonitorDO::getUnidadeId, unidades))
                        .forEach(m -> ids.add(m.getId()));
            }
        }
        emManutencao = Set.copyOf(ids);
        calculadoEm = System.currentTimeMillis();
        return emManutencao;
    }

    /** Títulos das janelas que valem agora para um monitor. */
    public List<String> valendoPara(MonitorDO monitor) {
        LocalDateTime agora = LocalDateTime.now();
        List<ManutencaoDO> todas = monManutencaoMapper.selectList(new LambdaQueryWrapperX<ManutencaoDO>().eq(ManutencaoDO::getAtiva, true));
        if (todas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> monitores = monitoresDe(todas.stream().map(ManutencaoDO::getId).toList());
        return todas.stream().filter(m -> valendo(m, agora))
                .filter(m -> monitores.getOrDefault(m.getId(), List.of()).contains(monitor.getId())
                        || (m.getUnidadeId() != null && m.getUnidadeId().equals(monitor.getUnidadeId())))
                .map(ManutencaoDO::getTitulo).toList();
    }

    public void esquecer() {
        calculadoEm = 0;
    }

    static boolean valendo(ManutencaoDO m, LocalDateTime agora) {
        return Regras.manutencaoValendo(m.getTipo(), Boolean.TRUE.equals(m.getAtiva()), m.getInicio(), m.getFim(), m.getDiasSemana(),
                m.getHoraInicio(), m.getHoraFim(), agora);
    }

    private Map<Long, List<Long>> monitoresDe(Collection<Long> manutencoes) {
        return monManutencaoMonitorMapper.selectList(new LambdaQueryWrapperX<ManutencaoMonitorDO>()
                        .in(ManutencaoMonitorDO::getManutencaoId, manutencoes)).stream()
                .collect(Collectors.groupingBy(ManutencaoMonitorDO::getManutencaoId,
                        Collectors.mapping(ManutencaoMonitorDO::getMonitorId, Collectors.toList())));
    }

    private static void validar(MonitorVOs.Manutencao r) {
        switch (r.getTipo()) {
            case "manual" -> {
            }
            case "unica" -> {
                if (r.getInicio() == null || r.getFim() == null || !r.getFim().isAfter(r.getInicio())) {
                    throw exception(MANUTENCAO_INVALIDA, "informe o início e um fim depois do início");
                }
            }
            case "semanal" -> {
                if (r.getDiasSemana() == null || r.getDiasSemana().isEmpty() || r.getDiasSemana().stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
                    throw exception(MANUTENCAO_INVALIDA, "escolha os dias da semana");
                }
                if (r.getHoraInicio() == null || r.getHoraFim() == null || r.getHoraInicio().equals(r.getHoraFim())) {
                    throw exception(MANUTENCAO_INVALIDA, "informe a hora de início e a de fim (diferentes)");
                }
            }
            default -> throw exception(MANUTENCAO_INVALIDA, "tipo deve ser manual, unica ou semanal");
        }
        if (r.getUnidadeId() == null && (r.getMonitorIds() == null || r.getMonitorIds().isEmpty())) {
            throw exception(MANUTENCAO_INVALIDA, "escolha os monitores ou uma unidade");
        }
    }

    private static MonitorVOs.Manutencao converter(ManutencaoDO m) {
        MonitorVOs.Manutencao vo = new MonitorVOs.Manutencao();
        vo.setId(m.getId());
        vo.setTitulo(m.getTitulo());
        vo.setDescricao(m.getDescricao());
        vo.setTipo(m.getTipo());
        vo.setAtiva(m.getAtiva());
        vo.setInicio(m.getInicio());
        vo.setFim(m.getFim());
        vo.setDiasSemana(new ArrayList<>(Regras.dias(m.getDiasSemana())));
        vo.setHoraInicio(m.getHoraInicio());
        vo.setHoraFim(m.getHoraFim());
        vo.setUnidadeId(m.getUnidadeId());
        return vo;
    }

}
