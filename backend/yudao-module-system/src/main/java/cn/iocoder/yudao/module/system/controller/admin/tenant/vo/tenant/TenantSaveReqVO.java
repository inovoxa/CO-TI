package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 租户创建/修改 Request VO")
@Data
public class TenantSaveReqVO {

    @Schema(description = "租户编号", example = "1024")
    private Long id;

    @Schema(description = "租户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道")
    @NotNull(message = "Informe o nome da empresa")
    private String name;

    @Schema(description = "联系人", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    @NotNull(message = "Informe o contato")
    private String contactName;

    @Schema(description = "联系手机", example = "15601691300")
    private String contactMobile;

    @Schema(description = "租户状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Situação da empresa")
    private Integer status;

    @Schema(description = "绑定域名数组", example = "https://www.iocoder.cn")
    private List<String> websites;

    @Schema(description = "租户套餐编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "Informe o plano da empresa")
    private Long packageId;

    @Schema(description = "过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Informe a validade")
    private LocalDateTime expireTime;

    @Schema(description = "账号数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "Informe o número de usuários")
    private Integer accountCount;

    // ========== 仅【创建】时，需要传递的字段 ==========

    @Schema(description = "用户账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,30}$", message = "O usuário só pode ter letras, números, ponto, hífen e sublinhado")
    @Size(min = 4, max = 30, message = "O usuário deve ter de 1 a 30 caracteres")
    private String username;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @Length(min = 4, max = 16, message = "A senha deve ter de 4 a 16 caracteres")
    private String password;

    @AssertTrue(message = "Informe usuário e senha")
    @JsonIgnore
    public boolean isUsernameValid() {
        return id != null // 修改时，不需要传递
                || (ObjectUtil.isAllNotEmpty(username, password)); // 新增时，必须都传递 username、password
    }

}
