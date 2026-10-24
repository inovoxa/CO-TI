package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Período de expediente num dia da semana (GLPI: glpi_calendarsegments).
 */
@TableName("itsm.calendario_periodo")
@KeySequence("itsm.calendario_periodo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarioPeriodoDO extends BaseDO {

    @TableId
    private Long id;
    private Long calendarioId;
    /** ISO: 1 = segunda ... 7 = domingo */
    private Integer diaSemana;
    private java.time.LocalTime inicio;
    /** 00:00 = meia-noite do fim do dia */
    private java.time.LocalTime fim;

}
