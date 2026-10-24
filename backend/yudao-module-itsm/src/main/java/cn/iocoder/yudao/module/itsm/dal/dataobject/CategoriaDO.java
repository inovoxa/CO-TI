package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Categoria de chamado em árvore (GLPI: glpi_itilcategories).
 *
 * <p>Sempre salva inteira: os campos opcionais gravam mesmo vazios (para "remover a equipe padrão" funcionar).
 */
@TableName("itsm.categoria")
@KeySequence("itsm.categoria_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaDO extends BaseDO {

    public static final Long RAIZ = 0L;

    @TableId
    private Long id;
    private Long parentId;
    private String nome;
    /** "Pai > Filho > Neto", mantido pelo serviço */
    private String nomeCompleto;
    private Integer nivel;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String codigo;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String descricao;
    /** Atribuição automática ao abrir (GLPI: users_id / groups_id da categoria) */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long tecnicoId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long equipeId;
    /** SLA desta categoria; vazio = o da prioridade */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long slaAtendimentoId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long slaSolucaoId;
    /** Aparece no portal de autoatendimento */
    private Boolean visivelPortal;
    private Boolean paraIncidente;
    private Boolean paraRequisicao;
    private Boolean ativo;
    private Integer ordem;

}
