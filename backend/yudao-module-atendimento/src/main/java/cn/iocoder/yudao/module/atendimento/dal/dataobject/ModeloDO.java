package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Modelo de mensagem aprovado na Meta (WhatsApp fora da janela de 24 h), copiado da conta na sincronização.
 */
@TableName("atendimento.modelo")
@KeySequence("atendimento.modelo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeloDO extends BaseDO {

    @TableId
    private Long id;
    private Long caixaId;
    private String nome;
    private String idioma;
    /** MARKETING | UTILITY | AUTHENTICATION */
    private String categoria;
    /** APPROVED | PENDING | REJECTED | PAUSED | DISABLED */
    private String status;
    /** Texto do corpo, com {{1}}, {{2}}... ou {{nome}} */
    private String corpo;
    private Integer variaveis;
    /** JSON dos componentes como a Meta devolveu */
    private String componentes;

}
