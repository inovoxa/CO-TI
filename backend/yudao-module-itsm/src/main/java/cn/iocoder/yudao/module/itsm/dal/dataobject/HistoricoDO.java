package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Histórico de alterações do chamado (GLPI: glpi_logs). Somente inserção.
 */
@TableName("itsm.historico")
@KeySequence("itsm.historico_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoDO {

    @TableId
    private Long id;
    private Long chamadoId;
    private LocalDateTime momento;
    private Long userId;
    private String usuario;
    /** Ex.: status, prioridade, categoria, tecnico+, tecnico-, solucao */
    private String campo;
    private String valorAntigo;
    private String valorNovo;

}
