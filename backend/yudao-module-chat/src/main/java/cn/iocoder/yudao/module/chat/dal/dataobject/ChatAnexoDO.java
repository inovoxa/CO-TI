package cn.iocoder.yudao.module.chat.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("chat.anexo")
@KeySequence("chat.anexo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnexoDO extends BaseDO {

    @TableId
    private Long id;
    private Long conversaId;
    private Long mensagemId;
    private String nome;
    private String tipoMime;
    private Long tamanho;
    private String caminho;
    private Boolean imagem;
    private Long enviadoPor;

}
