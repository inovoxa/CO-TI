package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Regra de automação: evento → condições → ações (Chatwoot: AutomationRule). Ver docs/specs/atendimento-automacoes.md.
 */
@TableName("atendimento.automacao")
@KeySequence("atendimento.automacao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomacaoDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String descricao;
    private String evento;
    /** JSON: [{campo, operador, valor}] */
    private String condicoes;
    /** JSON: [{tipo, valor}] */
    private String acoes;
    private Boolean ativa;
    private Integer ordem;
    private Long execucoes;
    private LocalDateTime ultimaExecucao;

}
