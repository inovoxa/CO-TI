package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Pedido de aprovação de um chamado (GLPI: glpi_ticketvalidations). status 2 aguardando, 3 aprovada, 4 recusada.
 */
@TableName("itsm.aprovacao")
@KeySequence("itsm.aprovacao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprovacaoDO extends BaseDO {

    @TableId
    private Long id;
    private Long chamadoId;
    private Long aprovadorId;
    /** vazio = sistema (regra ou formulário) */
    private Long solicitadoPor;
    private Integer status;
    private String comentarioPedido;
    private String comentarioResposta;
    private java.time.LocalDateTime pedidoEm;
    private java.time.LocalDateTime respondidoEm;

}
