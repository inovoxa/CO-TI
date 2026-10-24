package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * Quem usa uma licença: pessoa ou equipamento.
 */
@TableName("itsm.licenca_uso")
@KeySequence("itsm.licenca_uso_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicencaUsoDO extends BaseDO {

    @TableId
    private Long id;
    private Long licencaId;
    private Long userId;
    private String equipamento;
    private LocalDate desde;
    private String observacao;

}
