package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Resposta pronta, chamada por "/atalho" (Chatwoot: CannedResponse).
 */
@TableName("atendimento.resposta_pronta")
@KeySequence("atendimento.resposta_pronta_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespostaProntaDO extends BaseDO {

    @TableId
    private Long id;
    private String atalho;
    private String conteudo;

}
