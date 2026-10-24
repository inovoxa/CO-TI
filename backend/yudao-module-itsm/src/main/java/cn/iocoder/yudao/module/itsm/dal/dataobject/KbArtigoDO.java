package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Artigo da base de conhecimento (GLPI: glpi_knowbaseitems). A coluna "busca" é gerada no banco.
 */
@TableName("itsm.kb_artigo")
@KeySequence("itsm.kb_artigo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbArtigoDO extends BaseDO {

    @TableId
    private Long id;
    private String titulo;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String resumo;
    /** HTML saneado */
    private String conteudo;
    /** Texto puro do conteúdo, para busca e trechos */
    private String texto;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long categoriaId;
    /** Visível para todos no portal (GLPI: is_faq) */
    private Boolean faq;
    /** false = rascunho */
    private Boolean publicado;
    /** Aparece primeiro (GLPI: is_pinned) */
    private Boolean fixado;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDateTime inicioPublicacao;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDateTime fimPublicacao;
    private Long autorId;
    private Integer revisao;
    private Integer visualizacoes;
    private Integer util;
    private Integer naoUtil;

}
