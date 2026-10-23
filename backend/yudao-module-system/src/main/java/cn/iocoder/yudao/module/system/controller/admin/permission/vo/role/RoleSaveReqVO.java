package cn.iocoder.yudao.module.system.controller.admin.permission.vo.role;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import com.mzt.logapi.starter.annotation.DiffLogField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 角色创建/更新 Request VO")
@Data
public class RoleSaveReqVO {

    @Schema(description = "角色编号", example = "1")
    private Long id;

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "管理员")
    @NotBlank(message = "Informe o nome do perfil")
    @Size(max = 30, message = "O nome do perfil pode ter no máximo 30 caracteres")
    @DiffLogField(name = "角色名称")
    private String name;

    @NotBlank(message = "Informe o código do perfil")
    @Size(max = 100, message = "O código do perfil pode ter no máximo 100 caracteres")
    @Schema(description = "角色标志", requiredMode = Schema.RequiredMode.REQUIRED, example = "ADMIN")
    @DiffLogField(name = "角色标志")
    private String code;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "Informe a ordem")
    @DiffLogField(name = "显示顺序")
    private Integer sort;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @DiffLogField(name = "状态")
    @NotNull(message = "Informe a situação")
    @InEnum(value = CommonStatusEnum.class, message = "A situação deve ser {value}")
    private Integer status;

    @Schema(description = "备注", example = "我是一个角色")
    @Size(max = 500, message = "As observações podem ter no máximo 500 caracteres")
    @DiffLogField(name = "备注")
    private String remark;

}
