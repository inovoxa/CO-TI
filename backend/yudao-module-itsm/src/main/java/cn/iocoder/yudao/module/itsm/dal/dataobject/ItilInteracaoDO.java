package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Acompanhamento, tarefa ou solução de problema ou mudança.
 */
@TableName("itsm.itil_interacao")
@KeySequence("itsm.itil_interacao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItilInteracaoDO extends BaseDO {

    @TableId
    private Long id;
    private String objeto;
    private Long objetoId;
    /** InteracaoDO.ACOMPANHAMENTO, TAREFA, SOLUCAO */
    private String tipo;
    private Long userId;
    private String conteudo;
    private Integer estadoTarefa;
    private Long tecnicoId;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private Long tempoGastoSeg;

}
