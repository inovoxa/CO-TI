package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Fato para os relatórios, gravado quando acontece (Chatwoot: ReportingEvent).
 */
@TableName("atendimento.evento_relatorio")
@KeySequence("atendimento.evento_relatorio_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoRelatorioDO extends BaseDO {

    @TableId
    private Long id;
    /** primeira_resposta | resolucao */
    private String tipo;
    private Long conversaId;
    private Long caixaId;
    private Long agenteId;
    private Long valorSeg;
    private Long valorUtilSeg;
    private LocalDateTime ocorridoEm;

}
