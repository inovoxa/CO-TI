package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Equipe técnica que atende chamados (GLPI: glpi_groups com is_assign).
 */
@TableName("itsm.equipe")
@KeySequence("itsm.equipe_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipeDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String descricao;
    private String email;
    /** Grupo do AD que alimenta os membros (opcional) */
    private String grupoAd;
    private Boolean ativo;

}
