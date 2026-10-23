package cn.iocoder.yudao.module.system.controller.admin.user.vo.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;


@Schema(description = "管理后台 - 用户个人信息更新 Request VO")
@Data
public class UserProfileUpdateReqVO {

    @Schema(description = "用户昵称", example = "芋艿")
    @Size(max = 30, message = "O nome pode ter no máximo 100 caracteres")
    private String nickname;

    @Schema(description = "用户邮箱", example = "yudao@iocoder.cn")
    @Email(message = "E-mail inválido")
    @Size(max = 50, message = "O e-mail pode ter no máximo 128 caracteres")
    private String email;

    @Schema(description = "手机号码", example = "15601691300")
    @Length(min = 11, max = 11, message = "Celular inválido")
    private String mobile;

    @Schema(description = "用户性别，参见 SexEnum 枚举类", example = "1")
    private Integer sex;

    @Schema(description = "角色头像", example = "https://www.iocoder.cn/1.png")
    @URL(message = "Endereço da foto inválido")
    private String avatar;

}
