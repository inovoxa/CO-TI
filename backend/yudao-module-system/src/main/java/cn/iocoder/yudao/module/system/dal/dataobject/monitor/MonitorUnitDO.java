package cn.iocoder.yudao.module.system.dal.dataobject.monitor;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * Unidade / filial exibida no telão de monitoramento.
 * Cada unidade aponta para um departamento (system_dept), que é a raiz da sua árvore de setores.
 */
@TableName("monitor_unit")
@KeySequence("monitor_unit_seq") // usado só em Oracle/PostgreSQL; MySQL ignora
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorUnitDO extends BaseDO {

    @TableId
    private Long id;
    /** Departamento raiz da unidade (system_dept.id) */
    private Long deptId;
    /** Nome da unidade */
    private String name;
    /** Cidade/UF */
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** 0 = ativa, 1 = inativa (CommonStatusEnum) */
    private Integer status;

}
