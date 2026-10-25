package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pacote pedido para uma máquina e o que o agente respondeu.
 */
@TableName("cmdb.pacote_implantacao")
@KeySequence("cmdb.pacote_implantacao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacoteImplantacaoDO extends BaseDO {

    @TableId
    private Long id;
    private Long pacoteId;
    private Long ativoId;
    private String situacao;
    private String codigo;
    private Long deptId;
    private Long pedidoPor;
    private LocalDateTime pedidoEm;
    private LocalDateTime notificadoEm;
    private LocalDateTime concluidoEm;

}
