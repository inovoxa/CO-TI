package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Pessoa da equipe do projeto.
 */
@TableName("itsm.projeto_membro")
@KeySequence("itsm.projeto_membro_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjetoMembroDO extends BaseDO {

    @TableId
    private Long id;
    private Long projetoId;
    private Long userId;
    private String papel;

}
