package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Versão anterior de um artigo (GLPI: glpi_knowbaseitems_revisions).
 */
@TableName("itsm.kb_revisao")
@KeySequence("itsm.kb_revisao_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbRevisaoDO {

    @TableId
    private Long id;
    private Long artigoId;
    private Integer revisao;
    private String titulo;
    private String conteudo;
    private Long autorId;
    private java.time.LocalDateTime criadoEm;

}
