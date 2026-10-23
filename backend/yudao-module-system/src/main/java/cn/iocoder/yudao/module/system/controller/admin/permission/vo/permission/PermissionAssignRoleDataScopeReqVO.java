package cn.iocoder.yudao.module.system.controller.admin.permission.vo.permission;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.system.enums.permission.DataScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Set;

@Schema(description = "管理后台 - 赋予角色数据权限 Request VO")
@Data
public class PermissionAssignRoleDataScopeReqVO {

    @Schema(description = "角色编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe o perfil")
    private Long roleId;

    @Schema(description = "数据范围，参见 DataScopeEnum 枚举类", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe o alcance de dados")
    @InEnum(value = DataScopeEnum.class, message = "O alcance de dados deve ser {value}")
    private Integer dataScope;

    @Schema(description = "部门编号列表，只有范围类型为 DEPT_CUSTOM 时，该字段才需要", example = "1,3,5")
    private Set<Long> dataScopeDeptIds = Collections.emptySet(); // 兜底

}
