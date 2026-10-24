package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Chamado (incidente ou requisição). Estrutura equivalente à glpi_tickets.
 */
@TableName("itsm.chamado")
@KeySequence("itsm.chamado_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChamadoDO extends BaseDO {

    @TableId
    private Long id;
    private String titulo;
    /** HTML saneado */
    private String descricao;
    /** {@link cn.iocoder.yudao.module.itsm.enums.ChamadoTipo} */
    private Integer tipo;
    /** {@link cn.iocoder.yudao.module.itsm.enums.ChamadoStatus} */
    private Integer status;
    private Integer urgencia;
    private Integer impacto;
    private Integer prioridade;
    private Long categoriaId;
    private Long origemId;
    /** Unidade/departamento a que o chamado pertence (escopo de dados) */
    private Long deptId;
    /** Situação das aprovações: 1 nenhuma, 2 aguardando, 3 aprovada, 4 recusada (GLPI: global_validation) */
    private Integer validacao;

    // ---------- datas (GLPI: date, takeintoaccountdate, solvedate, closedate) ----------
    private LocalDateTime abertoEm;
    private LocalDateTime assumidoEm;
    private LocalDateTime solucionadoEm;
    private LocalDateTime fechadoEm;

    // ---------- SLA (preenchidos na F1b) ----------
    private Long slaAtendimentoId;
    private Long slaSolucaoId;
    /** Prazo para alguém assumir (GLPI: time_to_own) */
    private LocalDateTime prazoAtendimento;
    /** Prazo para solucionar (GLPI: time_to_resolve) */
    private LocalDateTime prazoSolucao;

    // ---------- espera e estatísticas, em segundos ----------
    /** Início da pendência atual (GLPI: begin_waiting_date) */
    private LocalDateTime inicioEspera;
    /** Tempo total em pendente (GLPI: waiting_duration) */
    private Long esperaSeg;
    /** Tempo em pendente dentro do expediente, descontado do SLA (GLPI: sla_waiting_duration) */
    private Long esperaSlaSeg;
    private Long tempoAssumirSeg;
    private Long tempoSolucaoSeg;
    private Long tempoFechamentoSeg;
    /** Soma do tempo gasto das tarefas (GLPI: actiontime) */
    private Long tempoGastoSeg;

    /** Quem registrou (pode ser diferente do solicitante; GLPI: users_id_recipient) */
    private Long registradoPor;
    /** Ex.: "uptime-kuma", "graylog", "whatsapp"; vazio = aberto por pessoa */
    private String origemAutomatica;
    /** ID na ferramenta de origem (ex.: id do monitor), para não abrir duplicado */
    private String idExterno;

}
