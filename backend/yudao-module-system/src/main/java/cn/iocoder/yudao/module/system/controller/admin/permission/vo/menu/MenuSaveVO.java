package cn.iocoder.yudao.module.system.controller.admin.permission.vo.menu;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "管理后台 - 菜单创建/修改 Request VO")
@Data
public class MenuSaveVO {

    @Schema(description = "菜单编号", example = "1024")
    private Long id;

    @Schema(description = "菜单名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道")
    @NotBlank(message = "Informe o nome do menu")
    @Size(max = 50, message = "O nome do menu pode ter no máximo 50 caracteres")
    private String name;

    @Schema(description = "权限标识,仅菜单类型为按钮时，才需要传递", example = "sys:menu:add")
    @Size(max = 100)
    private String permission;

    @Schema(description = "类型，参见 MenuTypeEnum 枚举类", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe o tipo")
    private Integer type;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "Informe a ordem")
    private Integer sort;

    @Schema(description = "父菜单 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "Informe o menu superior")
    private Long parentId;

    @Schema(description = "路由地址,仅菜单类型为菜单或者目录时，才需要传", example = "post")
    @Size(max = 200, message = "A rota pode ter no máximo 200 caracteres")
    private String path;

    @Schema(description = "菜单图标,仅菜单类型为菜单或者目录时，才需要传", example = "/menu/list")
    private String icon;

    @Schema(description = "组件路径,仅菜单类型为菜单时，才需要传", example = "system/post/index")
    @Size(max = 200, message = "O componente pode ter no máximo 255 caracteres")
    private String component;

    @Schema(description = "组件名", example = "SystemUser")
    private String componentName;

    @Schema(description = "状态,见 CommonStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe a situação")
    private Integer status;

    @Schema(description = "是否可见", example = "false")
    private Boolean visible;

    @Schema(description = "是否缓存", example = "false")
    private Boolean keepAlive;

    @Schema(description = "是否总是显示", example = "false")
    private Boolean alwaysShow;

}
