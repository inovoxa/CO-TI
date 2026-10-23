package cn.iocoder.yudao.module.system.controller.admin.auth.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.*;

@Schema(description = "管理后台 - Register Request VO")
@Data
public class AuthRegisterReqVO extends CaptchaVerificationReqVO {

    @Schema(description = "用户账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao")
    @NotBlank(message = "Informe o usuário")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,30}$", message = "O usuário só pode ter letras, números, ponto, hífen e sublinhado")
    @Size(min = 4, max = 30, message = "O usuário deve ter de 1 a 30 caracteres")
    private String username;

    @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    @NotBlank(message = "Informe o nome")
    @Size(max = 30, message = "O nome pode ter no máximo 100 caracteres")
    private String nickname;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotEmpty(message = "Informe a senha")
    @Length(min = 4, max = 16, message = "A senha deve ter de 4 a 16 caracteres")
    private String password;
}