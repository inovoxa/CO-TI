package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * Licença de software (GLPI: glpi_softwarelicenses). A chave fica cifrada.
 */
@TableName("itsm.licenca")
@KeySequence("itsm.licenca_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicencaDO extends BaseDO {

    @TableId
    private Long id;
    private String software;
    private String fabricante;
    private String nome;
    private String tipo;
    /** -1 = ilimitada */
    private Integer quantidade;
    private Boolean permiteExcesso;
    private String chaveCifrada;
    private LocalDate expira;
    private Integer alertaDias;
    private LocalDate alertaReferencia;
    private Long fornecedorId;
    private Long contratoId;
    private Long deptId;
    private Long responsavelId;
    private String observacao;
    private Boolean ativo;
    private String idExterno;

}
