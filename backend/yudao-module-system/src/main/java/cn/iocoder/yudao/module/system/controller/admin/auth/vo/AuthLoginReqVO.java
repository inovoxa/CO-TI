package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

@Schema(description = "管理后台 - 账号密码登录 Request VO，如果登录并绑定社交用户，需要传递 social 开头的参数")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthLoginReqVO extends CaptchaVerificationReqVO {

    @Schema(description = "账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudaoyuanma")
    @NotEmpty(message = "Informe o usuário")
    @Length(min = 1, max = 30, message = "Usuário deve ter de 1 a 30 caracteres")
    // CO-TI: aceita o sAMAccountName do AD (joao.silva, ana-souza, svc_backup)
    @Pattern(regexp = "^[a-zA-Z0-9._-]{1,30}$", message = "Usuário só pode ter letras, números, ponto, hífen e sublinhado")
    private String username;

    /** CO-TI: o login chega como no Windows (DOMINIO\joao.silva ou joao.silva@dominio); valida e autentica só a conta */
    public AuthLoginReqVO setUsername(String username) {
        this.username = contaDoLogin(username);
        return this;
    }

    /** "PMA\joao.silva", "joao.silva@pma.local" e "joao.silva" → "joao.silva" */
    public static String contaDoLogin(String login) {
        if (login == null) {
            return null;
        }
        String s = login.strip();
        int barra = s.lastIndexOf('\\');
        if (barra >= 0) {
            s = s.substring(barra + 1);
        }
        int arroba = s.indexOf('@');
        if (arroba >= 0) {
            s = s.substring(0, arroba);
        }
        return s;
    }

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "buzhidao")
    @NotEmpty(message = "Informe a senha")
    @Length(min = 1, max = 128, message = "Senha deve ter até 128 caracteres") // CO-TI: senhas do AD podem passar de 16
    private String password;

    // ========== 绑定社交登录时，需要传递如下参数 ==========

    @Schema(description = "社交平台的类型，参见 SocialTypeEnum 枚举值", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @InEnum(SocialTypeEnum.class)
    private Integer socialType;

    @Schema(description = "授权码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String socialCode;

    @Schema(description = "state", requiredMode = Schema.RequiredMode.REQUIRED, example = "9b2ffbc1-7425-4155-9894-9d5c08541d62")
    private String socialState;

    @AssertTrue(message = "Informe o código de autorização")
    public boolean isSocialCodeValid() {
        return socialType == null || StrUtil.isNotEmpty(socialCode);
    }

    @AssertTrue(message = "Informe o state")
    public boolean isSocialState() {
        return socialType == null || StrUtil.isNotEmpty(socialState);
    }

}