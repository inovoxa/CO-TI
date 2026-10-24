package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Conta de envio das notificações (uma por instalação; id = 1). A senha fica cifrada.
 */
@TableName("itsm.email_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailConfigDO {

    public static final Long UNICA = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;
    private Boolean ativo;
    private String smtpHost;
    private Integer smtpPorta;
    /** nenhuma | starttls | ssl */
    private String smtpSeguranca;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String smtpUsuario;
    private String smtpSenhaCifrada;
    private String remetenteEmail;
    private String remetenteNome;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String responderPara;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String urlPortal;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String assinatura;
    private LocalDateTime updateTime;

}
