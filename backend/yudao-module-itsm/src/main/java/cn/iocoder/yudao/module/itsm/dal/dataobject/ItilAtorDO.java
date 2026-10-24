package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Pessoa ou equipe num problema ou mudança (GLPI: glpi_problems_users, glpi_changes_groups...).
 */
@TableName("itsm.itil_ator")
@KeySequence("itsm.itil_ator_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItilAtorDO extends BaseDO {

    @TableId
    private Long id;
    /** "problema" ou "mudanca" */
    private String objeto;
    private Long objetoId;
    /** AtorTipo */
    private Integer tipo;
    private Long userId;
    private Long equipeId;

}
