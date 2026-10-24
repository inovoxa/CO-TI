package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Mensagem na fila de saída (outbox).
 */
@TableName("itsm.email_saida")
@KeySequence("itsm.email_saida_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSaidaDO {

    @TableId
    private Long id;
    private Long chamadoId;
    private String evento;
    private String para;
    private String assunto;
    private String corpo;
    private String messageId;
    /** pendente | enviando | enviado | erro */
    private String status;
    private Integer tentativas;
    private LocalDateTime proximaTentativa;
    private String erro;
    private LocalDateTime criadoEm;
    private LocalDateTime enviadoEm;

}
