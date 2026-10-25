package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * O que mudou num ativo (somente inclusão).
 */
@TableName("cmdb.historico")
@KeySequence("cmdb.historico_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoAtivoDO extends BaseDO {

    @TableId
    private Long id;
    private Long ativoId;
    private LocalDateTime quando;
    private String tipo;
    private String campo;
    private String antes;
    private String depois;
    private Long userId;

}
