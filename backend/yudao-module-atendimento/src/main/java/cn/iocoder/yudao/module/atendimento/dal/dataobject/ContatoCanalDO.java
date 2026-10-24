package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * O endereço do contato numa caixa (Chatwoot: ContactInbox).
 */
@TableName("atendimento.contato_canal")
@KeySequence("atendimento.contato_canal_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContatoCanalDO extends BaseDO {

    @TableId
    private Long id;
    private Long contatoId;
    private Long caixaId;
    /** Número do WhatsApp, chat do Telegram, id no sistema da API */
    private String origemId;

}
