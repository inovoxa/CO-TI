package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** SLA padrão de cada prioridade, usado quando a categoria não define. */
@TableName("itsm.sla_prioridade")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaPrioridadeDO {

    @TableId(type = IdType.INPUT)
    private Integer prioridade;
    /** null limpa o SLA (por isso grava mesmo quando vazio) */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long slaAtendimentoId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long slaSolucaoId;

}
