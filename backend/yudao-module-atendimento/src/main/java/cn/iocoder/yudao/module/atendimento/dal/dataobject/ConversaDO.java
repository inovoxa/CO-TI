package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Conversa (Chatwoot: Conversation).
 */
@TableName("atendimento.conversa")
@KeySequence("atendimento.conversa_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversaDO extends BaseDO {

    @TableId
    private Long id;
    private Long caixaId;
    private Long contatoId;
    private Long contatoCanalId;
    /** ConversaStatus */
    private Integer status;
    private Long agenteId;
    private Integer prioridade;
    private Long chamadoId;
    private LocalDateTime adiadaAte;
    private LocalDateTime primeiraRespostaEm;
    private LocalDateTime resolvidaEm;
    /** Última mensagem do contato (janela de 24 h do WhatsApp) */
    private LocalDateTime ultimaEntradaEm;
    private LocalDateTime ultimaAtividadeEm;
    private String atributos;
    /** Com o assistente de IA (pendente até o transbordo) */
    private Boolean robo;
    private Integer roboRespostas;
    private Integer roboFalhas;

}
