package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * Contrato (GLPI: glpi_contracts). Vigência calculada em dominio.Vigencia.
 */
@TableName("itsm.contrato")
@KeySequence("itsm.contrato_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContratoDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String numero;
    private String tipo;
    private Long fornecedorId;
    private Long deptId;
    /** Recebe os avisos de vencimento */
    private Long responsavelId;
    private LocalDate inicio;
    private Integer duracaoMeses;
    private Integer avisoPrevioMeses;
    /** Vigencia.NAO_RENOVA, TACITA, EXPRESSA */
    private Integer renovacao;
    private Integer alertaDias;
    private String numeroContabil;
    private String observacao;
    private Boolean encerrado;
    /** Prazo para o qual o aviso já foi enviado */
    private LocalDate alertaReferencia;
    private String idExterno;

}
