package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Credencial SNMP (v2c ou v3) tentada nas varreduras; segredos cifrados.
 */
@TableName("cmdb.snmp_credencial")
@KeySequence("cmdb.snmp_credencial_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnmpCredencialDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String versao;
    private String comunidadeCifrada;
    private String usuario;
    private String nivel;
    private String authProtocolo;
    private String authSenhaCifrada;
    private String privProtocolo;
    private String privSenhaCifrada;
    private Integer porta;
    private Integer ordem;
    private Boolean ativo;

}
