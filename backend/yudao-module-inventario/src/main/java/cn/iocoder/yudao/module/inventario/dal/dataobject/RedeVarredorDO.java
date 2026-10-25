package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Agente escolhido para varrer uma sub-rede (IPDiscover).
 */
@TableName("cmdb.rede_varredor")
@KeySequence("cmdb.rede_varredor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeVarredorDO extends BaseDO {

    @TableId
    private Long id;
    private String subrede;
    private String mascara;
    private Long ativoId;
    private LocalDateTime eleitoEm;
    private LocalDateTime ultimaVarredura;

}
