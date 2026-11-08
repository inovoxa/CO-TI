package cn.iocoder.yudao.module.monitoramento.fonte;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.dominio.Estados;
import cn.iocoder.yudao.module.monitoramento.service.MonitorService;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.LinkVO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.ListaVO;
import cn.iocoder.yudao.module.system.service.monitor.fonte.MonitorFontes;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fonte real da tela "Centro de Operações → Links por unidade" e do link e dos serviços de cada unidade no telão: lê o
 * monitoramento no lugar dos dados simulados do MVP.
 */
@Primary
@Component
public class MonitoramentoLinksFonte implements MonitorFontes.Links, MonitorFontes.IndicadoresLink {

    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private MonitorService monitorService;

    @Override
    public ListaVO<LinkVO> listarLinks(Long unidadeId, String filtro, int limite) {
        List<MonitorDO> base = monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().eq(MonitorDO::getAtivo, true)
                .eqIfPresent(MonitorDO::getUnidadeId, unidadeId).orderByAsc(MonitorDO::getOrdem).orderByAsc(MonitorDO::getNome));
        List<MonitorVOs.Resp> todos = monitorService.respostas(base);
        ListaVO<LinkVO> res = new ListaVO<>();
        List<LinkVO> links = todos.stream().map(MonitoramentoLinksFonte::link).toList();
        res.getContagens().put("problema", links.stream().filter(l -> !"up".equals(l.getEstado())).count());
        res.getContagens().put("links", links.stream().filter(l -> !"servico".equals(l.getPapel())).count());
        res.getContagens().put("servicos", links.stream().filter(l -> "servico".equals(l.getPapel())).count());
        res.getContagens().put("todos", (long) links.size());
        String f = filtro == null ? "todos" : filtro;
        List<LinkVO> lista = links.stream().filter(l -> switch (f) {
            case "problema" -> !"up".equals(l.getEstado());
            case "down", "lento" -> f.equals(l.getEstado());
            case "links" -> !"servico".equals(l.getPapel());
            case "servicos" -> "servico".equals(l.getPapel());
            default -> true;
        }).toList();
        res.setTotal(lista.size());
        res.setLista(new ArrayList<>(lista.subList(0, Math.min(lista.size(), limite <= 0 ? lista.size() : limite))));
        res.getResumo().put("uptimeMedio24h", links.stream().map(LinkVO::getUptime24h).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(100));
        res.getResumo().put("certificadosVencendo", links.stream().filter(l -> l.getCertificadoDias() != null && l.getCertificadoDias() < 30).count());
        return res;
    }

    /** Pior estado entre os monitores principais de cada unidade (fora > degradado > no ar). */
    @Override
    @DataPermission(enable = false)
    public Map<Long, MonitorFontes.LinkUnidade> linksPrincipais() {
        List<MonitorDO> principais = monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().eq(MonitorDO::getAtivo, true)
                .eq(MonitorDO::getPapel, "principal").isNotNull(MonitorDO::getUnidadeId));
        if (principais.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> uptime = monitorService.uptime24h(principais.stream().map(MonitorDO::getId).toList());
        Map<Long, MonitorFontes.LinkUnidade> out = new HashMap<>();
        principais.stream().collect(Collectors.groupingBy(MonitorDO::getUnidadeId)).forEach((unidade, lista) -> {
            MonitorDO pior = lista.stream().max(Comparator.comparingInt(MonitoramentoLinksFonte::gravidade)).orElseThrow();
            int g = gravidade(pior);
            String link = g == 2 ? "offline" : g == 1 ? "degradado" : "online";
            Double disp = lista.stream().map(m -> uptime.get(m.getId())).filter(Objects::nonNull).mapToDouble(Double::doubleValue)
                    .average().stream().boxed().findFirst().orElse(null);
            out.put(unidade, new MonitorFontes.LinkUnidade(link, g == 2 ? null : pior.getUltimaLatenciaMs(), disp));
        });
        return out;
    }

    /** Monitores ativos de cada unidade; os em manutenção e os ainda não verificados não entram na conta. */
    @Override
    @DataPermission(enable = false)
    public Map<Long, MonitorFontes.ServicosUnidade> servicosPorUnidade() {
        Map<Long, int[]> soma = new HashMap<>();
        for (MonitorDO m : monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().eq(MonitorDO::getAtivo, true)
                .isNotNull(MonitorDO::getUnidadeId))) {
            Integer e = m.getEstado();
            if (e == null || e == Estados.MANUTENCAO) {
                continue;
            }
            int[] s = soma.computeIfAbsent(m.getUnidadeId(), k -> new int[2]);
            s[0] += e == Estados.NO_AR ? 1 : 0;
            s[1]++;
        }
        Map<Long, MonitorFontes.ServicosUnidade> out = new HashMap<>();
        soma.forEach((unidade, s) -> out.put(unidade, new MonitorFontes.ServicosUnidade(s[0], s[1])));
        return out;
    }

    private static int gravidade(MonitorDO m) {
        Integer e = m.getEstado();
        if (e == null || e == Estados.MANUTENCAO) {
            return 0;
        }
        if (e == Estados.FORA) {
            return 2;
        }
        boolean lento = e == Estados.NO_AR && m.getLentoMs() != null && m.getUltimaLatenciaMs() != null && m.getUltimaLatenciaMs() > m.getLentoMs();
        return e == Estados.PENDENTE || lento ? 1 : 0;
    }

    private static LinkVO link(MonitorVOs.Resp r) {
        LinkVO l = new LinkVO();
        l.setId(r.getId());
        l.setNome(r.getNome());
        l.setDescricao(r.getDescricao());
        l.setTipo(switch (r.getTipo()) {
            case "http", "palavra", "json" -> "http";
            case "tcp" -> "port";
            default -> r.getTipo();
        });
        l.setAlvo(r.getAlvo() == null ? r.getTipo() : r.getAlvo() + (r.getPorta() == null ? "" : ":" + r.getPorta()));
        l.setUnidadeId(r.getUnidadeId());
        l.setUnidade(r.getUnidadeNome() == null ? "sem unidade" : r.getUnidadeNome());
        l.setPapel(r.getPapel());
        Integer e = r.getEstado();
        l.setEstado(e != null && e == Estados.FORA ? "down" : (e != null && e == Estados.PENDENTE) || Boolean.TRUE.equals(r.getLento()) ? "lento" : "up");
        l.setLatenciaMs(r.getUltimaLatenciaMs());
        l.setMensagem(r.getUltimaMsg());
        l.setDesde(r.getEstadoDesde());
        l.setHistorico(r.getHistorico().stream().map(b -> b.getEstado() == Estados.FORA ? "d" : b.getEstado() == Estados.PENDENTE ? "p" : "u")
                .collect(Collectors.toCollection(ArrayList::new)));
        l.setUptime24h(r.getUptime24h() == null ? 100.0 : r.getUptime24h());
        l.setUptime30d(r.getUptime30d() == null ? 100.0 : r.getUptime30d());
        l.setCertificadoDias(r.getCertDias());
        return l;
    }

}
