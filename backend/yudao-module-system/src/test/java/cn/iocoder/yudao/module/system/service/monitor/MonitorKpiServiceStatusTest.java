package cn.iocoder.yudao.module.system.service.monitor;

import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.UnitKpiVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cor da unidade no mapa: vermelho só para o que está fora agora. */
class MonitorKpiServiceStatusTest {

    @Test
    void foraAgora_vermelho() {
        assertEquals("critico", MonitorKpiService.calcularStatus(kpi("offline", 100.0)));
    }

    @Test
    void voltouComQuedaNoDia_amarelo() {
        // queda de uma hora já resolvida (disponibilidade de 24 h abaixo de 98%) não deixa a unidade vermelha
        assertEquals("atencao", MonitorKpiService.calcularStatus(kpi("online", 95.8)));
    }

    @Test
    void normal_verde() {
        assertEquals("ok", MonitorKpiService.calcularStatus(kpi("online", 99.9)));
    }

    private static UnitKpiVO kpi(String link, double disponibilidade) {
        return UnitKpiVO.builder().link(link).latenciaMs(8).disponibilidade(disponibilidade)
                .servicosNoAr(0).servicosTotal(0).chamadosAbertos(0).slaVencido(0).build();
    }

}
