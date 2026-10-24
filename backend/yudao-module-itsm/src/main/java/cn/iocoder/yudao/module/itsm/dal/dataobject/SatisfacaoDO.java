package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Pesquisa de satisfação de um chamado fechado (GLPI: glpi_ticketsatisfactions), nota de 1 a 5. */
@TableName("itsm.satisfacao")
@KeySequence("itsm.satisfacao_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfacaoDO {

    @TableId
    private Long id;
    private Long chamadoId;
    private LocalDateTime enviadaEm;
    private LocalDateTime respondidaEm;
    private Integer nota;
    private String comentario;

}
