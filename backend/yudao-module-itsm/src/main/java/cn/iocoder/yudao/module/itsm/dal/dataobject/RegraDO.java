package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Regra de negócio de chamado (GLPI: RuleTicket). Critérios e ações em JSON.
 */
@TableName("itsm.regra")
@KeySequence("itsm.regra_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegraDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String descricao;
    private Integer ordem;
    private Boolean ativo;
    /** E = todos os critérios; OU = qualquer um */
    private String combinacao;
    /** Não avalia as regras seguintes se esta casar */
    private Boolean parar;
    /** JSON: [{campo, operador, valor}] */
    private String criterios;
    /** JSON: [{acao, valor}] */
    private String acoes;

}
