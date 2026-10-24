package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Custo de contrato, projeto ou licença (GLPI: glpi_contractcosts, glpi_projectcosts).
 */
@TableName("itsm.custo")
@KeySequence("itsm.custo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustoDO extends BaseDO {

    @TableId
    private Long id;
    /** contrato | projeto | licenca */
    private String objeto;
    private Long objetoId;
    private String nome;
    private LocalDate inicio;
    private LocalDate fim;
    private BigDecimal valor;
    private Long orcamentoId;
    private String observacao;

}
