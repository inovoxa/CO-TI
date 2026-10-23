package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.packages;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 租户套餐精简 Response VO")
@Data
public class TenantPackageSimpleRespVO {

    @Schema(description = "套餐编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "Informe o plano")
    private Long id;

    @Schema(description = "套餐名", requiredMode = Schema.RequiredMode.REQUIRED, example = "VIP")
    @NotNull(message = "Informe o nome do plano")
    private String name;

}
