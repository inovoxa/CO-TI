package cn.iocoder.yudao.module.chat.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("chat.mensagem")
@KeySequence("chat.mensagem_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMensagemDO extends BaseDO {

    public static final String TEXTO = "texto";
    public static final String SISTEMA = "sistema";
    public static final String AUTO = "auto";

    @TableId
    private Long id;
    private Long conversaId;
    private Long seq;
    private Long remetenteId;
    private String tipo;
    private String conteudo;
    private Long respostaId;
    private String mencoes;
    private String clienteId;
    private LocalDateTime editadaEm;
    private LocalDateTime apagadaEm;

}
