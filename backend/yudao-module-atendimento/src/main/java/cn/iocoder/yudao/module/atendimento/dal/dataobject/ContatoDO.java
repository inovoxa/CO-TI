package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Quem fala com a central (Chatwoot: Contact).
 */
@TableName("atendimento.contato")
@KeySequence("atendimento.contato_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContatoDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    /** E.164, ex.: +5516999990000 */
    private String telefone;
    private String email;
    private String identificador;
    /** Pessoa do portal, quando o contato é da empresa */
    private Long userId;
    private String atributos;
    private String observacao;
    private Boolean bloqueado;
    private LocalDateTime ultimaAtividadeEm;

}
