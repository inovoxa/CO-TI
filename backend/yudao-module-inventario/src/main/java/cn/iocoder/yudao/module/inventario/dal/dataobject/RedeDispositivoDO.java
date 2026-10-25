package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Equipamento que apareceu na rede (um por MAC).
 */
@TableName("cmdb.rede_dispositivo")
@KeySequence("cmdb.rede_dispositivo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeDispositivoDO extends BaseDO {

    @TableId
    private Long id;
    private String mac;
    private String ip;
    private String nome;
    private String subrede;
    private String mascara;
    private Long ativoId;
    private Boolean ignorado;
    private String observacao;
    private Long vistoPor;
    private Long deptId;
    private LocalDateTime primeiraVez;
    private LocalDateTime ultimaVez;

}
