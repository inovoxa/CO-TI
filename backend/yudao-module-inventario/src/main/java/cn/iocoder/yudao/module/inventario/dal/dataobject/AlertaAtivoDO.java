package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Alerta do inventário: aberto enquanto a condição existe.
 */
@TableName("cmdb.alerta_ativo")
@KeySequence("cmdb.alerta_ativo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaAtivoDO extends BaseDO {

    @TableId
    private Long id;
    private String regra;
    private String objeto;
    private Long objetoId;
    private String titulo;
    private String detalhe;
    private Long deptId;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private Long chamadoId;

}
