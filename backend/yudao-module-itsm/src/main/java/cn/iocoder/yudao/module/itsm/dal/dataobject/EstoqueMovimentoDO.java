package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/** Histórico do item: só se acrescenta. */
@TableName("itsm.estoque_movimento")
@KeySequence("itsm.estoque_movimento_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstoqueMovimentoDO extends BaseDO {

    @TableId
    private Long id;
    private Long itemId;
    /** entrada | retirada | devolucao | manutencao | retorno | baixa */
    private String tipo;
    private Integer quantidade;
    private String pessoa;
    private String destino;
    private String motivo;
    /** Chamado que originou o movimento */
    private Long chamadoId;
    /** Quem registrou */
    private Long userId;
    private LocalDateTime quando;

}
