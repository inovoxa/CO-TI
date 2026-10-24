package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tarefa de projeto (GLPI: glpi_projecttasks).
 */
@TableName("itsm.projeto_tarefa")
@KeySequence("itsm.projeto_tarefa_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjetoTarefaDO extends BaseDO {

    @TableId
    private Long id;
    private Long projetoId;
    private Long tarefaPaiId;
    private String nome;
    private String descricao;
    private Long responsavelId;
    /** 1 a fazer, 2 em andamento, 3 concluída, 4 cancelada */
    private Integer situacao;
    private LocalDate inicioPrevisto;
    private LocalDate fimPrevisto;
    private LocalDate inicioReal;
    private LocalDate fimReal;
    private BigDecimal horasPrevistas;
    private BigDecimal horasGastas;
    private Integer percentual;
    private Boolean marco;
    private Integer ordem;

}
