package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Categoria da base de conhecimento, em árvore (GLPI: glpi_knowbaseitemcategories).
 */
@TableName("itsm.kb_categoria")
@KeySequence("itsm.kb_categoria_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbCategoriaDO extends BaseDO {

    public static final Long RAIZ = 0L;

    @TableId
    private Long id;
    private Long parentId;
    private String nome;
    /** "Pai > Filho", mantido pelo serviço */
    private String nomeCompleto;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String descricao;
    private Integer ordem;

}
