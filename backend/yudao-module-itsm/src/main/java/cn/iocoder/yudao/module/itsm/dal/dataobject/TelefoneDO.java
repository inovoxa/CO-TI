package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Contato da lista de ramais e telefones. Os campos opcionais podem voltar a vazio ao editar.
 */
@TableName("itsm.telefone")
@KeySequence("itsm.telefone_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelefoneDO extends BaseDO {

    @TableId
    private Long id;
    /** Pessoa, sala ou serviço */
    private String nome;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String setor;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unidade;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ramal;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String telefone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String observacao;

}
