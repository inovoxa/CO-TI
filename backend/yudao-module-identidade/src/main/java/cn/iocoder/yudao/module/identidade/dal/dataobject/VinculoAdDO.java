package cn.iocoder.yudao.module.identidade.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Liga um usuário do portal (system_users) à conta do AD. Quem tem vínculo só entra com a senha do AD.
 */
@TableName("identidade.vinculo_ad")
@KeySequence("identidade.vinculo_ad_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VinculoAdDO extends BaseDO {

    @TableId
    private Long id;
    /** system_users.id */
    private Long userId;
    /** objectGUID da conta no AD */
    private String guid;
    /** sAMAccountName */
    private String login;
    private String dn;
    /** DN do gestor (manager), para aprovações */
    private String gestorDn;
    /** Situação da conta no AD na última leitura */
    private Boolean ativoNoAd;
    private LocalDateTime ultimaSincronizacao;

}
