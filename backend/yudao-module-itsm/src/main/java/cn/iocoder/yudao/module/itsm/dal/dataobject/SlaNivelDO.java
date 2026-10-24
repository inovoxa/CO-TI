package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Nível de escalonamento (GLPI: glpi_slalevels).
 */
@TableName("itsm.sla_nivel")
@KeySequence("itsm.sla_nivel_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaNivelDO extends BaseDO {

    @TableId
    private Long id;
    private Long slaId;
    private String nome;
    /** Relativo ao prazo: negativo = antes de vencer */
    private Long tempoSeg;
    private Long atribuirEquipeId;
    private Long atribuirTecnicoId;
    private Integer novaPrioridade;
    private Boolean ativo;

}
