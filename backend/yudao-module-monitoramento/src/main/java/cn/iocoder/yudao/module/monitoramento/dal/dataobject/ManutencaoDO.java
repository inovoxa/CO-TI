package cn.iocoder.yudao.module.monitoramento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Janela de manutenção: os monitores dela não são verificados nem avisam.
 */
@TableName("monitor.manutencao")
@KeySequence("monitor.manutencao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManutencaoDO extends BaseDO {

    @TableId
    private Long id;
    private String titulo;
    private String descricao;
    private String tipo;
    private Boolean ativa;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private String diasSemana;
    private LocalTime horaInicio;
    private LocalTime horaFim;
    private Long unidadeId;

}
