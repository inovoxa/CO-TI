package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Caixa de entrada coletada por IMAP (GLPI: glpi_mailcollectors).
 */
@TableName("itsm.email_caixa")
@KeySequence("itsm.email_caixa_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCaixaDO extends cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO {

    @TableId
    private Long id;
    private String nome;
    private Boolean ativo;
    private String host;
    private Integer porta;
    /** nenhuma | starttls | ssl */
    private String seguranca;
    private String usuario;
    private String senhaCifrada;
    private String pasta;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pastaProcessados;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pastaRecusados;
    private Boolean aceitarDesconhecidos;
    private Boolean exigirAutenticacao;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoriaId;
    private Integer anexoMaxMb;
    private LocalDateTime ultimaColeta;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ultimoErro;

}
