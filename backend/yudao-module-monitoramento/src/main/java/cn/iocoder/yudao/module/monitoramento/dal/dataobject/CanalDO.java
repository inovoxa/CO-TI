package cn.iocoder.yudao.module.monitoramento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Canal de aviso dos monitores (e-mail, Matrix, Telegram, webhook).
 */
@TableName("monitor.canal")
@KeySequence("monitor.canal_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanalDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String tipo;
    private String config;
    private Boolean padrao;
    private Boolean ativo;

}
