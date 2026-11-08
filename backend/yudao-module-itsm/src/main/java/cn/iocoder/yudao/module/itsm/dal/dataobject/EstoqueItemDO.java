package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("itsm.estoque_item")
@KeySequence("itsm.estoque_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstoqueItemDO extends BaseDO {

    @TableId
    private Long id;
    private Long tipoId;
    private String descricao;
    /** IMEI, ICCID ou número de série */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String identificador;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String linha;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String patrimonio;
    /** novo | usado */
    private String condicao;
    /** disponivel | em_uso | manutencao | baixado */
    private String situacao;
    private Integer quantidade;
    private Integer minimo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dataCompra;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fornecedor;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String notaFiscal;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal valor;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate garantiaAte;
    /** Item usado: quem usava antes de entrar no estoque */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String usadoPor;
    /** Quem está com o item agora */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String responsavel;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String destino;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String observacao;

}
