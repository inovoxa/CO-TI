package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Regra de alerta do inventário (liga/desliga, limite e abertura de chamado).
 */
@TableName("cmdb.alerta_regra")
@KeySequence("cmdb.alerta_regra_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaRegraDO extends BaseDO {

    @TableId
    private Long id;
    private String codigo;
    private String nome;
    private Boolean ativo;
    private Boolean abrirChamado;
    private Integer limite;
    private Long categoriaId;
    private Integer urgencia;

}
