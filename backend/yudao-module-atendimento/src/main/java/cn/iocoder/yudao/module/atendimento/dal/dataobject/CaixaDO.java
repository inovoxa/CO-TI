package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Caixa de entrada: um canal atendido por um grupo de pessoas (Chatwoot: Inbox).
 */
@TableName("atendimento.caixa")
@KeySequence("atendimento.caixa_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"tokenHash", "webhookSegredo", "config"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaixaDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    /** api | whatsapp_cloud | whatsapp_evolution | telegram | widget */
    private String canal;
    private Boolean ativo;
    /** JSON do canal; segredos cifrados */
    private String config;
    /** Canal API: SHA-256 do token de entrada */
    private String tokenHash;
    private String webhookUrl;
    /** Canal API: segredo da assinatura das respostas, cifrado */
    private String webhookSegredo;
    private String saudacao;
    private Long equipeId;
    private Boolean atribuicaoAutomatica;
    /** 0 = sem limite */
    private Integer limitePorAgente;
    private Integer reabrirHoras;
    /** Calendário do Service Desk (expediente e feriados); null = sempre em horário */
    private Long calendarioId;
    /** Mandada quando a conversa abre ou reabre fora do expediente */
    private String mensagemForaHorario;

}
