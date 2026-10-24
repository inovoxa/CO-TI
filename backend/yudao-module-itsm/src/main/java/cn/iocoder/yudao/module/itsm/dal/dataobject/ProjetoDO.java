package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * Projeto (GLPI: glpi_projects).
 */
@TableName("itsm.projeto")
@KeySequence("itsm.projeto_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjetoDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String codigo;
    private String descricao;
    /** 1 planejado, 2 em andamento, 3 pausado, 4 concluído, 5 cancelado */
    private Integer situacao;
    private Integer prioridade;
    private Long deptId;
    private Long responsavelId;
    private LocalDate inicioPrevisto;
    private LocalDate fimPrevisto;
    private LocalDate inicioReal;
    private LocalDate fimReal;
    private Integer percentual;
    /** Percentual calculado pelas tarefas */
    private Boolean percentualAuto;
    private Long orcamentoId;
    private String idExterno;

}
