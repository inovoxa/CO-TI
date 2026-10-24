package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * O que avisar por e-mail e para quem (GLPI: glpi_notifications), com o modelo.
 */
@TableName("itsm.email_evento")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEventoDO {

    @TableId(type = IdType.INPUT)
    private String evento;
    private String nome;
    private Boolean ativo;
    private Boolean paraSolicitante;
    private Boolean paraObservador;
    private Boolean paraTecnico;
    private Boolean paraAprovador;
    private String assunto;
    private String corpo;
    private Integer ordem;
    /** Avisos com destinatários decididos pelo sistema (ex.: aprovadores da mudança); a tela não mostra os papéis */
    private String destinatariosFixos;
    /** Variáveis aceitas no modelo, para a tela */
    private String variaveis;

}
