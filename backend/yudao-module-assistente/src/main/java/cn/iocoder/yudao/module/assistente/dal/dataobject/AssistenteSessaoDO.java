package cn.iocoder.yudao.module.assistente.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Conversa de uma pessoa com o assistente interno.
 */
@TableName("ia.assistente_sessao")
@KeySequence("ia.assistente_sessao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistenteSessaoDO extends BaseDO {

    @TableId
    private Long id;
    private Long userId;
    private String titulo;
    private LocalDateTime atualizadaEm;

}
