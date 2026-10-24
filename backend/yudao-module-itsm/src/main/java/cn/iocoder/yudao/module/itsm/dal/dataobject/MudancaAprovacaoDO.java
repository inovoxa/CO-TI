package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pedido de aprovação de uma mudança (GLPI: glpi_changevalidations).
 */
@TableName("itsm.mudanca_aprovacao")
@KeySequence("itsm.mudanca_aprovacao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MudancaAprovacaoDO extends BaseDO {

    @TableId
    private Long id;
    private Long mudancaId;
    private Long aprovadorId;
    private Long solicitadoPor;
    /** Rodada de aprovação: a situação global só considera a última */
    private Integer rodada;
    /** Aprovacoes.AGUARDANDO, APROVADA, RECUSADA */
    private Integer status;
    private String comentarioPedido;
    private String comentarioResposta;
    private LocalDateTime pedidoEm;
    private LocalDateTime respondidoEm;

}
