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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Estado dos indicadores de TI de cada unidade + feed de ocorrências.
 *
 * <p>Nesta primeira versão os números são SIMULADOS. Para dados reais, troque o corpo de
 * {@link #lerIndicadores(MonitorUnitDO, UnitKpiVO)} por chamadas às ferramentas que a área de TI já usa:
 * <ul>
 *   <li><b>Zabbix</b> (API JSON-RPC): link, latência, disponibilidade, hosts online — agrupando os hosts
 *       por um "host group" com o nome/código da unidade;</li>
 *   <li><b>GLPI</b> (API REST): chamados abertos e com SLA vencido por entidade/localização;</li>
 *   <li><b>Wazuh</b> (API REST / OpenSearch): alertas de nível alto por agente/grupo da unidade;</li>
 *   <li><b>Zabbix + SNMP</b> dos sensores, PDUs e nobreaks: temperatura, energia, autonomia;</li>
 *   <li>ferramenta de backup (Bacula, Veeam, etc.): resultado da última janela.</li>
 * </ul>
 * A regra de status fica em {@link #calcularStatus(UnitKpiVO)} e vale para dados reais ou simulados.
 */
@Service
public class MonitorKpiService {

    private static final int MAX_OCORRENCIAS = 50;

    @Resource
    private MonitorUnitMapper monitorUnitMapper;
    /** Monitoramento real do link principal (módulo de monitoramento); sem ele, o link é simulado */
    @Resource
    private ObjectProvider<MonitorFontes.IndicadoresLink> indicadoresLink;

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
        Map<Long, MonitorFontes.LinkUnidade> reais = null;
        if (fonteLink != null) {
            try {
                reais = fonteLink.linksPrincipais();
            } catch (RuntimeException e) {
                reais = null; // o telão não para por causa do monitoramento
            }
        }
        for (MonitorUnitDO unidade : getUnidadesAtivas()) {
            UnitKpiVO anterior = estado.get(unidade.getId());
            UnitKpiVO novo = lerIndicadores(unidade, anterior, reais == null);
            if (reais != null) {
                aplicarLinkReal(novo, reais.get(unidade.getId()));
            }
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

    // ===================== REGRAS (valem para dados reais) =====================

    /** Regra de cor da unidade. Ajuste os limites conforme o acordo de nível de serviço do órgão/empresa. */
    public static String calcularStatus(UnitKpiVO k) {
        double hosts = k.getHostsTotal() == 0 ? 1 : (double) k.getHostsOnline() / k.getHostsTotal();
        boolean nobreak = "nobreak".equals(k.getEnergia());
        if ("offline".equals(k.getLink()) || k.getIncidentesSeg() >= 2 || hosts < 0.90
                || k.getDisponibilidade() < 98.0 || k.getTemperaturaC() >= 30
                || (nobreak && k.getAutonomiaMin() != null && k.getAutonomiaMin() < 15)
                || k.getStoragePct() >= 95) {
            return "critico";
        }
        if ("degradado".equals(k.getLink()) || k.getLatenciaMs() > 120 || k.getSlaVencido() > 0
                || k.getIncidentesSeg() >= 1 || hosts < 0.97 || k.getTemperaturaC() >= 27
                || nobreak || k.getStoragePct() >= 85 || k.getCpuPct() >= 85
                || Boolean.FALSE.equals(k.getBackupOk())) {
            return "atencao";
        }
        return "ok";
    }

    private void compararEGerarOcorrencias(MonitorUnitDO u, UnitKpiVO a, UnitKpiVO n, List<OcorrenciaVO> out) {
        long agora = System.currentTimeMillis();
        if (!Objects.equals(a.getLink(), n.getLink())) {
            String nivel = "offline".equals(n.getLink()) ? "critico" : "degradado".equals(n.getLink()) ? "atencao" : "info";
            String texto = "online".equals(n.getLink()) ? "Link principal normalizado"
                    : "offline".equals(n.getLink()) ? "Link principal fora do ar" : "Link principal degradado (perda de pacotes)";
            out.add(new OcorrenciaVO(u.getId(), nivel, "rede", texto, agora));
        }
        if (n.getIncidentesSeg() > a.getIncidentesSeg()) {
            out.add(new OcorrenciaVO(u.getId(), "critico", "seguranca", "Novo incidente de segurança de nível alto", agora));
        }
        if (n.getSlaVencido() > a.getSlaVencido()) {
            out.add(new OcorrenciaVO(u.getId(), "atencao", "atendimento", "Chamado ultrapassou o SLA", agora));
        }
        if (!Objects.equals(a.getEnergia(), n.getEnergia())) {
            out.add("nobreak".equals(n.getEnergia())
                    ? new OcorrenciaVO(u.getId(), "atencao", "datacenter", "Queda de energia: operando no nobreak", agora)
                    : new OcorrenciaVO(u.getId(), "info", "datacenter", "Energia da concessionária restabelecida", agora));
        }
        if (a.getTemperaturaC() < 27 && n.getTemperaturaC() >= 27) {
            out.add(new OcorrenciaVO(u.getId(), "atencao", "datacenter",
                    String.format("Temperatura da sala em %.1f °C", n.getTemperaturaC()), agora));
        }
        if (Boolean.TRUE.equals(a.getBackupOk()) && Boolean.FALSE.equals(n.getBackupOk())) {
            out.add(new OcorrenciaVO(u.getId(), "atencao", "datacenter", "Falha na rotina de backup", agora));
        }
        if (a.getHostsOnline() - n.getHostsOnline() >= 3) { // 1 ou 2 servidores oscilando não viram ocorrência
            int caiu = a.getHostsOnline() - n.getHostsOnline();
            out.add(new OcorrenciaVO(u.getId(), "atencao", "ativos",
                    caiu + (caiu == 1 ? " servidor deixou" : " servidores deixaram") + " de responder", agora));
        }
    }

    /** Link, latência e disponibilidade vêm do monitor principal da unidade; sem monitor, a unidade fica no ar. */
    private static void aplicarLinkReal(UnitKpiVO k, MonitorFontes.LinkUnidade real) {
        if (real == null) {
            k.setLink("online");
            k.setLatenciaMs(0);
            k.setDisponibilidade(100.0);
            return;
        }
        k.setLink(real.link());
        k.setLatenciaMs(real.latenciaMs() == null ? 0 : real.latenciaMs());
        if (real.disponibilidade() != null) {
            k.setDisponibilidade(Math.round(real.disponibilidade() * 100) / 100d);
        }
    }

    // ===================== SIMULAÇÃO — substitua por dados reais =====================

    private UnitKpiVO lerIndicadores(MonitorUnitDO unidade, UnitKpiVO a, boolean linkSimulado) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (a == null) {
            int hosts = 15 + r.nextInt(120);
            return UnitKpiVO.builder().unitId(unidade.getId()).link("online")
                    .latenciaMs(15 + r.nextInt(60)).disponibilidade(99.5 + r.nextInt(50) / 100d)
                    .hostsOnline(hosts).hostsTotal(hosts)
                    .temperaturaC(21 + r.nextInt(30) / 10d).energia("rede").autonomiaMin(null)
                    .cpuPct(25 + r.nextInt(40)).storagePct(50 + r.nextInt(30)).backupOk(true)
                    .chamadosAbertos(r.nextInt(25)).slaVencido(0).incidentesSeg(0).build();
        }
        // As taxas abaixo são por leitura (5 s). Cada grandeza volta para um valor típico
        // (reversão à média), para a simulação não "derivar" e deixar tudo vermelho com o tempo.

        // energia: queda rara; no nobreak a autonomia cai até a concessionária voltar
        String energia = a.getEnergia();
        Integer autonomia = a.getAutonomiaMin();
        if ("rede".equals(energia) && r.nextInt(10000) < 3) {
            energia = "nobreak";
            autonomia = 40 + r.nextInt(30);
        } else if ("nobreak".equals(energia)) {
            autonomia = Math.max(0, autonomia - 1);
            if (r.nextInt(100) < 15) { energia = "rede"; autonomia = null; }
        }
        // temperatura: tende a 22,5 °C; no nobreak parte do ar-condicionado desliga e ela sobe
        double temp = a.getTemperaturaC() + (22.5 - a.getTemperaturaC()) * 0.15 + r.nextInt(-2, 3) / 10d
                + ("nobreak".equals(energia) ? 0.5 : 0);
        temp = Math.round(Math.max(18, Math.min(35, temp)) * 10) / 10d;
        // CPU: tende a 45%
        int cpu = (int) Math.round(Math.max(5, Math.min(99, a.getCpuPct() + (45 - a.getCpuPct()) * 0.1 + r.nextInt(-5, 6))));
        // storage: cresce devagar; acima de 82% a rotina de limpeza/expansão devolve espaço
        int storage = a.getStoragePct() + (r.nextInt(100) < 2 ? 1 : 0) - (a.getStoragePct() >= 82 && r.nextInt(100) < 10 ? 3 : 0);
        storage = Math.max(30, Math.min(99, storage));
        boolean backup = a.getBackupOk() ? r.nextInt(1000) >= 2 : r.nextInt(100) < 15;

        // link: ~0,3% de chance de cair e ~0,8% de degradar por leitura; volta em média em ~3 leituras
        String link = linkSimulado ? a.getLink() : "online"; // com monitoramento real, o link vem dele depois
        int p = r.nextInt(1000);
        if (!linkSimulado) {
            link = "online";
        } else if ("online".equals(link)) {
            link = p < 3 ? "offline" : p < 11 ? "degradado" : "online";
        } else if (p < 300) {
            link = "online";
        }
        int normal = "offline".equals(a.getLink()) ? 30 : a.getLatenciaMs() - ("degradado".equals(a.getLink()) ? 120 : 0);
        normal = Math.max(8, (int) Math.round(normal + (30 - normal) * 0.2 + r.nextInt(-5, 6)));
        int latencia = "offline".equals(link) ? 0 : normal + ("degradado".equals(link) ? 120 : 0);

        int hostsOnline = "offline".equals(link) ? (int) (a.getHostsTotal() * 0.4)
                : Math.min(a.getHostsTotal(), Math.max(0, a.getHostsOnline() + (r.nextInt(100) < 2 ? -1 : 1 + r.nextInt(3))));

        double disp = Math.min(100, Math.max(95, a.getDisponibilidade()
                + ("offline".equals(link) ? -0.3 : "degradado".equals(link) ? -0.03 : 0.05)));

        // chamados: equilíbrio perto de 12 abertos por unidade; SLA vencido e incidentes são raros
        int chamados = Math.max(0, a.getChamadosAbertos() + (r.nextInt(100) < 30 ? 1 : 0)
                - (r.nextInt(1000) < a.getChamadosAbertos() * 25 ? 1 : 0));
        int sla = Math.max(0, Math.min(chamados, a.getSlaVencido() + (r.nextInt(100) < 3 ? 1 : 0) - (r.nextInt(100) < 15 ? 1 : 0)));
        int incidentes = Math.max(0, a.getIncidentesSeg() + (r.nextInt(1000) < 8 ? 1 : 0) - (r.nextInt(100) < 10 ? 1 : 0));

        return a.toBuilder().link(link).latenciaMs(latencia)
                .disponibilidade(Math.round(disp * 100) / 100d).hostsOnline(hostsOnline)
                .temperaturaC(temp).energia(energia).autonomiaMin(autonomia)
                .cpuPct(cpu).storagePct(storage).backupOk(backup)
                .chamadosAbertos(chamados).slaVencido(sla).incidentesSeg(incidentes).build();
    }

}
