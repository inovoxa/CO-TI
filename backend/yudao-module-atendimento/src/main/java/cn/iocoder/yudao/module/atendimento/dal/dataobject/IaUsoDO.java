package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Uma chamada ao provedor de IA (custo e auditoria). O texto das mensagens não é copiado para cá.
 */
@TableName("atendimento.ia_uso")
@KeySequence("atendimento.ia_uso_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IaUsoDO extends BaseDO {

    @TableId
    private Long id;
    /** assistente | sugerir | resumir | reescrever | teste */
    private String finalidade;
    private Long provedorId;
    private Long conversaId;
    private Long userId;
    private String modelo;
    private Integer tokensEntrada;
    private Integer tokensSaida;
    private Integer duracaoMs;
    private String erro;
    private LocalDateTime criadoEm;

}
