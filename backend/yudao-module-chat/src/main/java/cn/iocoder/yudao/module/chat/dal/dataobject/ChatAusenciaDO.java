package cn.iocoder.yudao.module.chat.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

@TableName("chat.ausencia")
@KeySequence("chat.ausencia_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAusenciaDO extends BaseDO {

    @TableId
    private Long id;
    private Long userId;
    private Boolean ativa;
    private String mensagem;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate inicio;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate fim;

}
