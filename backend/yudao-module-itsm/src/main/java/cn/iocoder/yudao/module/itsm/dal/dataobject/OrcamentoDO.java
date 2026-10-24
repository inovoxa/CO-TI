package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Orçamento (GLPI: glpi_budgets). O gasto é a soma dos custos ligados a ele.
 */
@TableName("itsm.orcamento")
@KeySequence("itsm.orcamento_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcamentoDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String tipo;
    private Long deptId;
    private LocalDate inicio;
    private LocalDate fim;
    private BigDecimal valor;
    private String observacao;

}
