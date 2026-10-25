package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Peça ou periférico de um ativo, como o agente informou.
 */
@TableName("cmdb.ativo_componente")
@KeySequence("cmdb.ativo_componente_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponenteDO extends BaseDO {

    @TableId
    private Long id;
    private Long ativoId;
    private String tipo;
    private String chave;
    private String descricao;
    private String dados;

}
