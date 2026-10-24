package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Problema: a causa de um ou mais incidentes (GLPI: glpi_problems).
 */
@TableName("itsm.problema")
@KeySequence("itsm.problema_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemaDO extends BaseDO {

    @TableId
    private Long id;
    private String titulo;
    private String descricao;
    private Integer status;
    private Integer urgencia;
    private Integer impacto;
    private Integer prioridade;
    private Long categoriaId;
    private Long deptId;
    private LocalDateTime abertoEm;
    /** Prazo combinado para solucionar (GLPI: time_to_resolve) */
    private LocalDateTime prazo;
    private LocalDateTime solucionadoEm;
    private LocalDateTime fechadoEm;
    /** O que o problema afeta (GLPI: impactcontent) */
    private String impactos;
    /** Causa raiz (GLPI: causecontent) */
    private String causas;
    /** Como o problema aparece (GLPI: symptomcontent) */
    private String sintomas;
    private Long tempoGastoSeg;
    private Long registradoPor;
    private String idExterno;

}
