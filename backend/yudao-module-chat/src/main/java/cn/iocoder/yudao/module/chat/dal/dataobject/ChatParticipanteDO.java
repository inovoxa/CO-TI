package cn.iocoder.yudao.module.chat.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("chat.participante")
@KeySequence("chat.participante_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipanteDO extends BaseDO {

    public static final String ADMIN = "admin";
    public static final String MEMBRO = "membro";

    @TableId
    private Long id;
    private Long conversaId;
    private Long userId;
    private String papel;
    private Long ultimaLidaSeq;
    /** Só vê as mensagens depois deste número */
    private Long desdeSeq;

}
