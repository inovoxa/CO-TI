package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Acordo de nível de serviço (GLPI: glpi_slas).
 */
@TableName("itsm.sla")
@KeySequence("itsm.sla_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    /** 1 = atendimento (TTO), 0 = solução (TTR), como no GLPI */
    private Integer tipo;
    private Integer tempo;
    /** minuto | hora | dia | mes */
    private String unidade;
    /** vazio = 24x7 */
    private Long calendarioId;
    private Boolean fimExpediente;
    private String descricao;
    private Boolean ativo;

}
