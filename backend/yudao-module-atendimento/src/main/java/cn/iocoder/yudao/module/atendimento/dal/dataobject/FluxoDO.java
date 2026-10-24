package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Estado do fluxo do catálogo numa conversa (docs/specs/atendimento-fluxo-whatsapp.md).
 */
@TableName("atendimento.fluxo")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "respostas")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FluxoDO extends BaseDO {

    @TableId(type = IdType.INPUT)
    private Long conversaId;
    private String etapa;
    private Long formularioId;
    private Integer indice;
    /** JSON cifrado */
    private String respostas;
    private String dados;
    private String sensiveis;
    private Long ultimaMensagem;
    private LocalDateTime atualizadoEm;

}
