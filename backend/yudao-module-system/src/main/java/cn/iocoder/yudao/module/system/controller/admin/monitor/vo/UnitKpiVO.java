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

    // ---- Rede / conectividade (fonte típica: Zabbix, Prometheus) ----
    /** online | degradado | offline — link principal da unidade */
    private String link;
    /** Latência até a sede, em ms */
    private Integer latenciaMs;
    /** Disponibilidade dos serviços nas últimas 24 h, em % */
    private Double disponibilidade;

    // ---- Ativos (fonte típica: Zabbix, GLPI, NetBox) ----
    /** Servidores/equipamentos monitorados respondendo */
    private Integer hostsOnline;
    private Integer hostsTotal;

    // ---- Datacenter / sala técnica (fonte típica: Zabbix via SNMP de sensores, PDUs e nobreaks) ----
    /** Temperatura do ambiente, em °C */
    private Double temperaturaC;
    /** rede | nobreak — de onde vem a energia agora */
    private String energia;
    /** Autonomia restante do nobreak, em minutos (só faz sentido com energia = nobreak) */
    private Integer autonomiaMin;
    /** Uso médio de CPU dos servidores, em % */
    private Integer cpuPct;
    /** Ocupação do armazenamento (storage), em % */
    private Integer storagePct;
    /** Backup da última janela concluído com sucesso */
    private Boolean backupOk;

    // ---- Suporte / atendimento (fonte típica: GLPI, OTRS/Znuny) ----
    /** Chamados abertos */
    private Integer chamadosAbertos;
    /** Chamados com SLA vencido */
    private Integer slaVencido;

    // ---- Segurança da informação (fonte típica: Wazuh, SIEM) ----
    /** Incidentes de segurança abertos (alta/crítica) */
    private Integer incidentesSeg;

    /** ok | atencao | critico — calculado a partir dos itens acima */
    private String status;
    /** Momento da leitura (epoch millis) */
    private Long atualizadoEm;

}
