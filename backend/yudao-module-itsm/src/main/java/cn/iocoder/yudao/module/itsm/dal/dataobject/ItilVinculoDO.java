package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Vínculo de problema ou mudança com chamado, ou de mudança com problema.
 */
@TableName("itsm.itil_vinculo")
@KeySequence("itsm.itil_vinculo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItilVinculoDO extends BaseDO {

    @TableId
    private Long id;
    private String objeto;
    private Long objetoId;
    /** "chamado" ou "problema" */
    private String alvo;
    private Long alvoId;

}
