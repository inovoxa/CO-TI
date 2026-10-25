package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Item do catálogo de software (nome e editora).
 */
@TableName("cmdb.software")
@KeySequence("cmdb.software_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String editora;
    private String chave;

}
