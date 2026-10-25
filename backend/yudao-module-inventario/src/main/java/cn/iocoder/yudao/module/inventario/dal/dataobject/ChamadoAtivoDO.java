package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Vínculo entre chamado do Service Desk e ativo.
 */
@TableName("cmdb.chamado_ativo")
@KeySequence("cmdb.chamado_ativo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChamadoAtivoDO extends BaseDO {

    @TableId
    private Long id;
    private Long chamadoId;
    private Long ativoId;
    private Long userId;
    private LocalDateTime criadoEm;

}
