package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mensagem a entregar no canal (fila com novas tentativas).
 */
@TableName("atendimento.envio")
@KeySequence("atendimento.envio_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvioDO {

    @TableId
    private Long id;
    private Long mensagemId;
    /** pendente | enviando | enviado | erro */
    private String status;
    private Integer tentativas;
    private LocalDateTime proximaTentativa;
    private String erro;
    private LocalDateTime criadoEm;
    private LocalDateTime enviadoEm;

}
