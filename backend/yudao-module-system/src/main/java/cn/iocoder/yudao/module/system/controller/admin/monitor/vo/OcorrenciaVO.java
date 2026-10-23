package cn.iocoder.yudao.module.system.controller.admin.monitor.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ocorrência exibida no feed do telão (queda de link, incidente, SLA estourado...).
 * Enviada pelo WebSocket com o tipo "monitor-ocorrencia".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcorrenciaVO {

    private Long unitId;
    /** info | atencao | critico */
    private String nivel;
    /** rede | ativos | datacenter | atendimento | seguranca */
    private String categoria;
    private String texto;
    private Long momento;

}
