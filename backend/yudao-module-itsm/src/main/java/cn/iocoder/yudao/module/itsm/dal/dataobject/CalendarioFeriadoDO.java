package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Feriado que vale num calendário (GLPI: glpi_calendars_holidays).
 */
@TableName("itsm.calendario_feriado")
@KeySequence("itsm.calendario_feriado_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarioFeriadoDO extends BaseDO {

    @TableId
    private Long id;
    private Long calendarioId;
    private Long feriadoId;

}
