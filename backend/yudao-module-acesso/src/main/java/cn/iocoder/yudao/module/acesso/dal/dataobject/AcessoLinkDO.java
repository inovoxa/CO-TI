package cn.iocoder.yudao.module.acesso.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("portal.link")
@KeySequence("portal.link_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcessoLinkDO extends BaseDO {

    @TableId
    private Long id;
    private String titulo;
    private String url;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String descricao;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String categoria;
    /** Vazio = público */
    private Long donoId;
    private Integer ordem;

}
