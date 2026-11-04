package cn.iocoder.yudao.module.chat.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/** O texto de antes de editar ou apagar, para apuração; nenhuma tela do chat o mostra. */
@TableName("chat.mensagem_historico")
@KeySequence("chat.mensagem_historico_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMensagemHistoricoDO extends BaseDO {

    @TableId
    private Long id;
    private Long mensagemId;
    private String acao;
    private String conteudo;
    private Long userId;
    private LocalDateTime quando;

}
