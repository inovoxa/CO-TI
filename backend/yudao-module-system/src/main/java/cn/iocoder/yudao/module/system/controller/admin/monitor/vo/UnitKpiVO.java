package cn.iocoder.yudao.module.system.controller.admin.monitor.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Indicadores de TI de uma unidade, enviados pelo WebSocket a cada atualização.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UnitKpiVO {

    private Long unitId;

    // ---- Rede: monitor principal da unidade (módulo de monitoramento) ----
    /** online | degradado | offline — link principal da unidade */
    private String link;
    /** Latência do link principal, em ms */
    private Integer latenciaMs;
    /** Disponibilidade do link principal nas últimas 24 h, em % */
    private Double disponibilidade;

    // ---- Serviços: todos os monitores ativos da unidade (módulo de monitoramento) ----
    private Integer servicosNoAr;
    private Integer servicosTotal;

    // ---- Atendimento (Service Desk) ----
    /** Chamados abertos */
    private Integer chamadosAbertos;
    /** Chamados com SLA vencido */
    private Integer slaVencido;

    /** ok | atencao | critico — calculado a partir dos itens acima */
    private String status;
    /** Momento da leitura (epoch millis) */
    private Long atualizadoEm;

}
