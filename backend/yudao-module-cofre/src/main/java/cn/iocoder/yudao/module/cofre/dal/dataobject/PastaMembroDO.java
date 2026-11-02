package cn.iocoder.yudao.module.cofre.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("cofre.pasta_membro")
@KeySequence("cofre.pasta_membro_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PastaMembroDO extends BaseDO {

    /** Vê as senhas */
    public static final int VER = 1;
    /** Também cadastra, altera e exclui senhas */
    public static final int EDITAR = 2;
    /** Também decide quem entra na pasta */
    public static final int GERENCIAR = 3;

    @TableId
    private Long id;
    private Long pastaId;
    private Long userId;
    private Integer papel;

}
