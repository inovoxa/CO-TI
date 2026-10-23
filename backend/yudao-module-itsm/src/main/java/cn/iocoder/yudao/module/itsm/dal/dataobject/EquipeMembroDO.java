package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("itsm.equipe_membro")
@KeySequence("itsm.equipe_membro_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipeMembroDO extends BaseDO {

    @TableId
    private Long id;
    private Long equipeId;
    private Long userId;
    /** Supervisor vê e redistribui a fila da equipe */
    private Boolean supervisor;

}
