package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Mensagem de uma conversa (Chatwoot: Message).
 */
@TableName("atendimento.mensagem")
@KeySequence("atendimento.mensagem_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MensagemDO extends BaseDO {

    @TableId
    private Long id;
    private Long conversaId;
    /** MensagemTipo */
    private Integer tipo;
    /** Nota interna: o contato não vê */
    private Boolean privada;
    /** contato | agente | robo | sistema */
    private String remetenteTipo;
    private Long remetenteId;
    /** Texto puro */
    private String conteudo;
    private String formato;
    /** JSON: [{url, tipo, nome, tamanho}] */
    private String anexos;
    private String idExterno;
    /** pendente | enviada | entregue | lida | falhou */
    private String status;
    private String erro;
    private Long respondeA;

}
