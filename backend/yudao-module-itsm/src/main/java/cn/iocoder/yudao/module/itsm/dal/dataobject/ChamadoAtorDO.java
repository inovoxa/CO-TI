package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Pessoa ou equipe ligada ao chamado (GLPI: glpi_tickets_users + glpi_groups_tickets numa tabela só).
 * Exatamente um de userId / equipeId / email vem preenchido.
 */
@TableName("itsm.chamado_ator")
@KeySequence("itsm.chamado_ator_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChamadoAtorDO extends BaseDO {

    @TableId
    private Long id;
    private Long chamadoId;
    /** {@link cn.iocoder.yudao.module.itsm.enums.AtorTipo} */
    private Integer tipo;
    private Long userId;
    private Long equipeId;
    /** Solicitante externo sem conta (ex.: e-mail recebido) */
    private String email;
    private Boolean notificar;

}
