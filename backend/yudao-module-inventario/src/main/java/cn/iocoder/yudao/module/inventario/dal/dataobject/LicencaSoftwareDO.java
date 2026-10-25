package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Item do catálogo de software que conta como uso de uma licença do ITSM.
 */
@TableName("cmdb.licenca_software")
@KeySequence("cmdb.licenca_software_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicencaSoftwareDO extends BaseDO {

    @TableId
    private Long id;
    private Long licencaId;
    private Long softwareId;
    private Long userId;

}
