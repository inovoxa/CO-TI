package cn.iocoder.yudao.module.monitoramento.dal.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Uma verificação (heartbeat) em monitor.batida (hypertable do TimescaleDB, sem id).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatidaDO {

    private Long monitorId;
    private LocalDateTime momento;
    private Integer estado;
    private Integer latenciaMs;
    private String msg;
    private Boolean importante;

}
