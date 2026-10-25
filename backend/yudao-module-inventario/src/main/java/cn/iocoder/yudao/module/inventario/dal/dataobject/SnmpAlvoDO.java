package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * IP consultado por SNMP: credencial que respondeu e resultado da última tentativa.
 */
@TableName("cmdb.snmp_alvo")
@KeySequence("cmdb.snmp_alvo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnmpAlvoDO extends BaseDO {

    @TableId
    private Long id;
    private String ip;
    private Long credencialId;
    private Long ativoId;
    private LocalDateTime ultimaTentativa;
    private LocalDateTime ultimaResposta;
    private String erro;

}
