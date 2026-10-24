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
 * Histórico de alterações de problemas e mudanças (GLPI: glpi_logs). Somente inserção.
 */
@TableName("itsm.itil_historico")
@KeySequence("itsm.itil_historico_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItilHistoricoDO {

    @TableId
    private Long id;
    private String objeto;
    private Long objetoId;
    private LocalDateTime momento;
    private Long userId;
    private String usuario;
    private String campo;
    private String valorAntigo;
    private String valorNovo;

}
