package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Artigo usado num chamado (GLPI: glpi_knowbaseitems_items).
 */
@TableName("itsm.kb_artigo_chamado")
@KeySequence("itsm.kb_artigo_chamado_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbArtigoChamadoDO {

    @TableId
    private Long id;
    private Long artigoId;
    private Long chamadoId;
    private Long userId;
    private java.time.LocalDateTime criadoEm;

}
