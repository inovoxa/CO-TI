package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Mudança na infraestrutura ou nos serviços (GLPI: glpi_changes).
 */
@TableName("itsm.mudanca")
@KeySequence("itsm.mudanca_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MudancaDO extends BaseDO {

    @TableId
    private Long id;
    private String titulo;
    private String descricao;
    /** MudancaTipo */
    private Integer tipo;
    private Integer status;
    private Integer urgencia;
    private Integer impacto;
    private Integer prioridade;
    private Long categoriaId;
    private Long deptId;
    private LocalDateTime abertoEm;
    private LocalDateTime prazo;
    /** Janela de execução planejada */
    private LocalDateTime inicioPrevisto;
    private LocalDateTime fimPrevisto;
    /** Aplicada em */
    private LocalDateTime solucionadoEm;
    private LocalDateTime fechadoEm;
    /** GLPI: impactcontent */
    private String analiseImpacto;
    /** GLPI: controlistcontent */
    private String planoControle;
    /** GLPI: rolloutplancontent */
    private String planoImplantacao;
    /** GLPI: backoutplancontent */
    private String planoRetorno;
    /** GLPI: checklistcontent */
    private String checklist;
    /** Aprovacoes.NENHUMA..RECUSADA */
    private Integer validacao;
    private Long tempoGastoSeg;
    private Long registradoPor;
    private String idExterno;

}
