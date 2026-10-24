package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Macro: ações que o atendente executa numa conversa com um clique (Chatwoot: Macro).
 */
@TableName("atendimento.macro")
@KeySequence("atendimento.macro_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MacroDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    /** pessoal | todos */
    private String visibilidade;
    private Long donoId;
    /** JSON: [{tipo, valor}] */
    private String acoes;

}
