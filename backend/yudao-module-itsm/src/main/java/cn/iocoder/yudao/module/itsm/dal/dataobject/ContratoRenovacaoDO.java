package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * Renovação expressa registrada.
 */
@TableName("itsm.contrato_renovacao")
@KeySequence("itsm.contrato_renovacao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContratoRenovacaoDO extends BaseDO {

    @TableId
    private Long id;
    private Long contratoId;
    private Integer meses;
    private LocalDate fimAntes;
    private LocalDate fimDepois;
    private String observacao;

}
