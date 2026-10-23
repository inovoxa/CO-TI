package cn.iocoder.yudao.module.identidade.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Segundo fator (TOTP) de um usuário. O segredo fica cifrado com a chave-mestra.
 */
@TableName("identidade.mfa_totp")
@KeySequence("identidade.mfa_totp_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"segredoCifrado", "codigosRecuperacao"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaTotpDO extends BaseDO {

    @TableId
    private Long id;
    private Long userId;
    private String segredoCifrado;
    /** false = cadastro iniciado, aguardando o primeiro código para confirmar */
    private Boolean ativo;
    /** Último passo de 30 s aceito: códigos do mesmo passo ou de antes são recusados */
    private Long ultimoPasso;
    /** JSON com os hashes BCrypt dos códigos de recuperação ainda não usados */
    private String codigosRecuperacao;
    private LocalDateTime ativadoEm;

}
