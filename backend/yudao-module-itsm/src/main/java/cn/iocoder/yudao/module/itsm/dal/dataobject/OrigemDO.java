package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Canal por onde o chamado ou o acompanhamento chegou (GLPI: glpi_requesttypes).
 */
@TableName("itsm.origem")
@KeySequence("itsm.origem_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrigemDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    /** Usada quando o chamado vem do portal */
    private Boolean padraoPortal;
    /** Usada quando o chamado vem por e-mail */
    private Boolean padraoEmail;
    private Boolean ativo;

}
