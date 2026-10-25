package cn.iocoder.yudao.module.monitoramento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Canal de aviso ligado a um monitor.
 */
@TableName("monitor.monitor_canal")
@KeySequence("monitor.monitor_canal_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorCanalDO extends BaseDO {

    @TableId
    private Long id;
    private Long monitorId;
    private Long canalId;

}
