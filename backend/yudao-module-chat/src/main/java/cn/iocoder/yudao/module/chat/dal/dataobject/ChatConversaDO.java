package cn.iocoder.yudao.module.chat.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("chat.conversa")
@KeySequence("chat.conversa_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversaDO extends BaseDO {

    public static final String DIRETA = "direta";
    public static final String GRUPO = "grupo";

    @TableId
    private Long id;
    private String tipo;
    private String nome;
    private String parChave;
    private Long ultimaSeq;
    private LocalDateTime ultimaMensagemEm;
    private Long criadoPor;

}
