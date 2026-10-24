package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Chamado, problema ou mudança ligado ao projeto (GLPI: Itil_Project).
 */
@TableName("itsm.projeto_vinculo")
@KeySequence("itsm.projeto_vinculo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjetoVinculoDO extends BaseDO {

    @TableId
    private Long id;
    private Long projetoId;
    private String objeto;
    private Long objetoId;

}
