package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Item da linha do tempo do chamado: acompanhamento, tarefa ou solução (GLPI: glpi_itilfollowups,
 * glpi_tickettasks e glpi_itilsolutions). Uma tabela só deixa a linha do tempo numa consulta ordenada.
 */
@TableName("itsm.interacao")
@KeySequence("itsm.interacao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteracaoDO extends BaseDO {

    public static final String ACOMPANHAMENTO = "acompanhamento";
    public static final String TAREFA = "tarefa";
    public static final String SOLUCAO = "solucao";

    /** Tarefa: 0 = informativa, 1 = a fazer, 2 = feita (GLPI: Planning::INFO/TODO/DONE) */
    public static final int TAREFA_INFO = 0;
    public static final int TAREFA_A_FAZER = 1;
    public static final int TAREFA_FEITA = 2;

    /** Solução: 1 = sem aprovação pendente, 2 = aguardando, 3 = aprovada, 4 = recusada (CommonITILValidation) */
    public static final int SOLUCAO_SEM_APROVACAO = 1;
    public static final int SOLUCAO_AGUARDANDO = 2;
    public static final int SOLUCAO_APROVADA = 3;
    public static final int SOLUCAO_RECUSADA = 4;

    @TableId
    private Long id;
    private Long chamadoId;
    private String tipo;
    private Long userId;
    /** HTML saneado */
    private String conteudo;
    /** Visível só para a equipe técnica */
    private Boolean privado;
    private Long origemId;

    // ---------- tarefa ----------
    private Integer estadoTarefa;
    private Long tecnicoId;
    private Long equipeId;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private Long tempoGastoSeg;

    // ---------- solução ----------
    private Integer statusSolucao;
    private Long avaliadoPor;
    private LocalDateTime avaliadoEm;

}
