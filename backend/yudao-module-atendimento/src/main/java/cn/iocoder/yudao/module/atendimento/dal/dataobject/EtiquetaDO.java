package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Etiqueta de conversa (Chatwoot: Label).
 */
@TableName("atendimento.etiqueta")
@KeySequence("atendimento.etiqueta_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtiquetaDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String cor;
    private String descricao;

}
