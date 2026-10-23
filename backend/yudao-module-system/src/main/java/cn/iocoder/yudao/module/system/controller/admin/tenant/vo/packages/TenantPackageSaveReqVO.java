package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.packages;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@Schema(description = "管理后台 - 租户套餐创建/修改 Request VO")
@Data
public class TenantPackageSaveReqVO {

    @Schema(description = "套餐编号", example = "1024")
    private Long id;

    @Schema(description = "套餐名", requiredMode = Schema.RequiredMode.REQUIRED, example = "VIP")
    @NotEmpty(message = "Informe o nome do plano")
    private String name;

    @Schema(description = "状态，参见 CommonStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe a situação")
    @InEnum(value = CommonStatusEnum.class, message = "A situação deve ser {value}")
    private Integer status;

    @Schema(description = "备注", example = "好")
    private String remark;

    @Schema(description = "关联的菜单编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Informe os menus do plano")
    private Set<Long> menuIds;

}
