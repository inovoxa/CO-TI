package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Fornecedor (GLPI: glpi_suppliers).
 */
@TableName("itsm.fornecedor")
@KeySequence("itsm.fornecedor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FornecedorDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String tipo;
    /** CNPJ ou CPF, só dígitos */
    private String documento;
    private String email;
    private String telefone;
    private String site;
    private String contato;
    private String endereco;
    private String cidade;
    private String uf;
    private String cep;
    private String observacao;
    private Boolean ativo;
    private String idExterno;

}
