package cn.iocoder.yudao.module.system.controller.admin.mail.vo.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 邮箱账号创建/修改 Request VO")
@Data
public class MailAccountSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudaoyuanma@123.com")
    @NotNull(message = "Informe o e-mail")
    @Email(message = "E-mail inválido")
    private String mail;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao")
    @NotNull(message = "Informe o usuário")
    private String username;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotNull(message = "Informe a senha")
    private String password;

    @Schema(description = "SMTP 服务器域名", requiredMode = Schema.RequiredMode.REQUIRED, example = "www.iocoder.cn")
    @NotNull(message = "Informe o servidor SMTP")
    private String host;

    @Schema(description = "SMTP 服务器端口", requiredMode = Schema.RequiredMode.REQUIRED, example = "80")
    @NotNull(message = "Informe a porta SMTP")
    private Integer port;

    @Schema(description = "是否开启 ssl", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "Informe se usa SSL")
    private Boolean sslEnable;

    @Schema(description = "是否开启 starttls", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "Informe se usa STARTTLS")
    private Boolean starttlsEnable;

}
