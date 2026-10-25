package cn.iocoder.yudao.module.assistente.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pergunta (user) ou resposta (assistant) numa conversa com o assistente interno.
 */
@TableName("ia.assistente_mensagem")
@KeySequence("ia.assistente_mensagem_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistenteMensagemDO extends BaseDO {

    @TableId
    private Long id;
    private Long sessaoId;
    private String papel;
    private String conteudo;
    /** Consultas feitas para responder (JSON) */
    private String ferramentas;
    private Boolean erro;
    private LocalDateTime criadaEm;

}
