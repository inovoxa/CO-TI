package cn.iocoder.yudao.module.cofre.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Senha guardada no cofre. O id vem da sequência antes do insert, porque entra na cifragem.
 */
@TableName("cofre.segredo")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"senhaCifrada", "notasCifradas"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SegredoDO extends BaseDO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long pastaId;
    private String nome;
    private String usuario;
    private String url;
    private String senhaCifrada;
    private String notasCifradas;
    private LocalDateTime senhaAlteradaEm;

}
