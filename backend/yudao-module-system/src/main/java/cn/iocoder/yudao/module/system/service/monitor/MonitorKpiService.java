package cn.iocoder.yudao.module.system.service.monitor;

import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.OcorrenciaVO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.UnitKpiVO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import cn.iocoder.yudao.module.system.service.monitor.fonte.MonitorFontes;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.annotation.Resource; // no branch master (JDK 8) troque por javax.annotation.Resource
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

/**
 * Estado dos indicadores de TI de cada unidade + feed de ocorrências do telão.
 *
 * <p>Só dados reais, vindos dos módulos do CO-TI:
 * <ul>
 *   <li><b>Monitoramento</b>: link principal (estado, latência, disponibilidade 24 h) e serviços no ar;</li>
 *   <li><b>Service Desk</b>: chamados abertos e com SLA vencido.</li>
 * </ul>
 * Datacenter (temperatura, energia, backup) e segurança ficam de fora até existir uma integração que os meça.
 * A regra de cor fica em {@link #calcularStatus(UnitKpiVO)}.
 */
@Service
public class MonitorKpiService {

    private static final int MAX_OCORRENCIAS = 50;

    @Resource
    private MonitorUnitMapper monitorUnitMapper;
    @Resource
    private ObjectProvider<MonitorFontes.IndicadoresLink> indicadoresLink;
    @Resource
    private ObjectProvider<MonitorFontes.IndicadoresChamados> indicadoresChamados;

    /** unitId -> última leitura */
    private final Map<Long, UnitKpiVO> estado = new ConcurrentHashMap<>();
    /** ocorrências mais recentes primeiro */
    private final Deque<OcorrenciaVO> ocorrencias = new ConcurrentLinkedDeque<>();

    public List<MonitorUnitDO> getUnidadesAtivas() {
        return monitorUnitMapper.selectActiveList();
    }

    public UnitKpiVO getKpi(Long unitId) {
        return estado.get(unitId);
    }

    public List<OcorrenciaVO> getOcorrencias() {
        return new ArrayList<>(ocorrencias);
    }

    /** Resultado de uma rodada de atualização. */
    public static class Rodada {
        public final List<UnitKpiVO> leituras = new ArrayList<>();
        public final List<OcorrenciaVO> novasOcorrencias = new ArrayList<>();
    }

    /** Atualiza todas as unidades ativas, gera ocorrências das mudanças e devolve tudo. */
    public Rodada atualizarTodas() {
        Rodada rodada = new Rodada();
        MonitorFontes.IndicadoresLink fonteLink = indicadoresLink.getIfAvailable();
        MonitorFontes.IndicadoresChamados fonteChamados = indicadoresChamados.getIfAvailable();
        Map<Long, MonitorFontes.LinkUnidade> links = ler(fonteLink == null ? null : fonteLink::linksPrincipais);
        Map<Long, MonitorFontes.ServicosUnidade> servicos = ler(fonteLink == null ? null : fonteLink::servicosPorUnidade);
        Map<Long, MonitorFontes.ChamadosUnidade> chamados = ler(fonteChamados == null ? null : fonteChamados::chamadosPorUnidade);
        for (MonitorUnitDO unidade : getUnidadesAtivas()) {
            UnitKpiVO anterior = estado.get(unidade.getId());
            UnitKpiVO novo = UnitKpiVO.builder().unitId(unidade.getId()).build();
            aplicarLink(novo, links.get(unidade.getId()));
            MonitorFontes.ServicosUnidade s = servicos.get(unidade.getId());
            novo.setServicosNoAr(s == null ? 0 : s.noAr());
            novo.setServicosTotal(s == null ? 0 : s.total());
            MonitorFontes.ChamadosUnidade c = chamados.get(unidade.getId());
            novo.setChamadosAbertos(c == null ? 0 : c.abertos());
            novo.setSlaVencido(c == null ? 0 : c.slaVencido());
            novo.setStatus(calcularStatus(novo));
            novo.setAtualizadoEm(System.currentTimeMillis());
            estado.put(unidade.getId(), novo);
            rodada.leituras.add(novo);
            if (anterior != null) {
                compararEGerarOcorrencias(unidade, anterior, novo, rodada.novasOcorrencias);
            }
        }
        for (OcorrenciaVO o : rodada.novasOcorrencias) {
            ocorrencias.addFirst(o);
        }
        while (ocorrencias.size() > MAX_OCORRENCIAS) {
            ocorrencias.pollLast();
        }
        return rodada;
    }

    /** Uma fonte fora do ar não para o telão: a unidade só fica sem aquele dado nesta rodada. */
    private static <T> Map<Long, T> ler(Supplier<Map<Long, T>> fonte) {
        if (fonte == null) {
            return Map.of();
        }
        try {
            Map<Long, T> m = fonte.get();
            return m == null ? Map.of() : m;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    // ===================== REGRAS =====================

    /** Regra de cor da unidade. Ajuste os limites conforme o acordo de nível de serviço do órgão/empresa. */
    public static String calcularStatus(UnitKpiVO k) {
        double servicos = k.getServicosTotal() == 0 ? 1 : (double) k.getServicosNoAr() / k.getServicosTotal();
        if ("offline".equals(k.getLink()) || servicos < 0.90 || k.getDisponibilidade() < 98.0) {
            return "critico";
        }
        if ("degradado".equals(k.getLink()) || k.getLatenciaMs() > 120 || k.getSlaVencido() > 0 || servicos < 1) {
            return "atencao";
        }
        return "ok";
    }

    private void compararEGerarOcorrencias(MonitorUnitDO u, UnitKpiVO a, UnitKpiVO n, List<OcorrenciaVO> out) {
        long agora = System.currentTimeMillis();
        if (!Objects.equals(a.getLink(), n.getLink())) {
            String nivel = "offline".equals(n.getLink()) ? "critico" : "degradado".equals(n.getLink()) ? "atencao" : "info";
            String texto = "online".equals(n.getLink()) ? "Link principal normalizado"
                    : "offline".equals(n.getLink()) ? "Link principal fora do ar" : "Link principal lento ou instável";
            out.add(new OcorrenciaVO(u.getId(), nivel, "rede", texto, agora));
        }
        int foraAntes = a.getServicosTotal() - a.getServicosNoAr();
        int foraAgora = n.getServicosTotal() - n.getServicosNoAr();
        if (foraAgora > foraAntes) {
            int sairam = foraAgora - foraAntes;
            out.add(new OcorrenciaVO(u.getId(), "atencao", "ativos",
                    sairam + (sairam == 1 ? " serviço saiu do ar" : " serviços saíram do ar"), agora));
        } else if (foraAntes > 0 && foraAgora == 0) {
            out.add(new OcorrenciaVO(u.getId(), "info", "ativos", "Todos os serviços de volta ao ar", agora));
        }
        if (n.getSlaVencido() > a.getSlaVencido()) {
            int novos = n.getSlaVencido() - a.getSlaVencido();
            out.add(new OcorrenciaVO(u.getId(), "atencao", "atendimento",
                    novos == 1 ? "Chamado ultrapassou o SLA" : novos + " chamados ultrapassaram o SLA", agora));
        }
    }

    /** Link, latência e disponibilidade vêm do monitor principal da unidade; sem monitor, a unidade fica no ar. */
    private static void aplicarLink(UnitKpiVO k, MonitorFontes.LinkUnidade real) {
        k.setLink(real == null ? "online" : real.link());
        k.setLatenciaMs(real == null || real.latenciaMs() == null ? 0 : real.latenciaMs());
        k.setDisponibilidade(real == null || real.disponibilidade() == null ? 100.0
                : Math.round(real.disponibilidade() * 100) / 100d);
    }

}
