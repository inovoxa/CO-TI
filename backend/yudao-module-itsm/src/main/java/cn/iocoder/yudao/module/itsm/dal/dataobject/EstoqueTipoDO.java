package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("itsm.estoque_tipo")
@KeySequence("itsm.estoque_tipo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstoqueTipoDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    /** unidade | quantidade */
    private String controle;
    private Boolean ativo;

}
