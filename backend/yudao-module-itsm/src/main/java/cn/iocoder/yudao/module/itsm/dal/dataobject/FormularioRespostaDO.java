package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respostas enviadas num formulário (uma por chamado).
 */
@TableName("itsm.formulario_resposta")
@KeySequence("itsm.formulario_resposta_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioRespostaDO {

    @TableId
    private Long id;
    private Long formularioId;
    private Long chamadoId;
    private Long userId;
    /** JSON {name: valor} */
    private String respostas;
    private java.time.LocalDateTime criadoEm;

}
