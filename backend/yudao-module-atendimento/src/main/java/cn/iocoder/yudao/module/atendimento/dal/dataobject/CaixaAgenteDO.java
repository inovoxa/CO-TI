package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Pessoa que atende uma caixa.
 */
@TableName("atendimento.caixa_agente")
@KeySequence("atendimento.caixa_agente_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaixaAgenteDO extends BaseDO {

    @TableId
    private Long id;
    private Long caixaId;
    private Long userId;

}
