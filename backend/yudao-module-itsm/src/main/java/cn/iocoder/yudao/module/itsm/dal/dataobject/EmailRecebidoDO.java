package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * E-mail lido de uma caixa: evita processar duas vezes e mostra o que foi recusado.
 */
@TableName("itsm.email_recebido")
@KeySequence("itsm.email_recebido_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRecebidoDO {

    @TableId
    private Long id;
    private Long caixaId;
    private String messageId;
    private String remetente;
    private String assunto;
    private Long chamadoId;
    /** chamado | acompanhamento | recusado | ignorado */
    private String resultado;
    private String motivo;
    private LocalDateTime recebidoEm;

}
