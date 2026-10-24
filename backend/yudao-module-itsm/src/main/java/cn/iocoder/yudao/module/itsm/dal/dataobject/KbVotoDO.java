package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "Este artigo ajudou?" — um voto por pessoa. Chave composta (artigo, pessoa): gravado por SQL no mapper.
 */
@TableName("itsm.kb_voto")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbVotoDO {

    private Long artigoId;
    private Long userId;
    private Boolean util;
    private java.time.LocalDateTime votadoEm;

}
