package cn.iocoder.yudao.module.monitoramento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Monitor incluído numa janela de manutenção.
 */
@TableName("monitor.manutencao_monitor")
@KeySequence("monitor.manutencao_monitor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManutencaoMonitorDO extends BaseDO {

    @TableId
    private Long id;
    private Long manutencaoId;
    private Long monitorId;

}
