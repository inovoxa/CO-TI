package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Webhook de um provedor (Meta, Telegram) já conferido, à espera de processamento.
 */
@TableName("atendimento.webhook_entrada")
@KeySequence("atendimento.webhook_entrada_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEntradaDO extends BaseDO {

    @TableId
    private Long id;
    /** whatsapp_cloud | instagram | telegram */
    private String canal;
    private Long caixaId;
    private String corpo;
    /** pendente | processando | ok | erro */
    private String status;
    private Integer tentativas;
    private String erro;
    private LocalDateTime proximaTentativa;
    private LocalDateTime recebidoEm;
    private LocalDateTime processadoEm;

}
