package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * Software instalado num ativo.
 */
@TableName("cmdb.ativo_software")
@KeySequence("cmdb.ativo_software_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtivoSoftwareDO extends BaseDO {

    @TableId
    private Long id;
    private Long ativoId;
    private Long softwareId;
    private String versao;
    private LocalDate instaladoEm;
    private String arquitetura;
    private String pasta;

}
