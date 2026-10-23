package cn.iocoder.yudao.module.infra.controller.admin.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 参数配置创建/修改 Request VO")
@Data
public class ConfigSaveReqVO {

    @Schema(description = "参数配置序号", example = "1024")
    private Long id;

    @Schema(description = "参数分组", requiredMode = Schema.RequiredMode.REQUIRED, example = "biz")
    @NotEmpty(message = "Informe a categoria")
    @Size(max = 50, message = "O nome pode ter no máximo 50 caracteres")
    private String category;

    @Schema(description = "参数名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "数据库名")
    @NotBlank(message = "Informe o nome")
    @Size(max = 100, message = "O nome pode ter no máximo 100 caracteres")
    private String name;

    @Schema(description = "参数键名", requiredMode = Schema.RequiredMode.REQUIRED, example = "yunai.db.username")
    @NotBlank(message = "Informe a chave")
    @Size(max = 100, message = "A chave pode ter no máximo 100 caracteres")
    private String key;

    @Schema(description = "参数键值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotBlank(message = "Informe o valor")
    @Size(max = 500, message = "O valor pode ter no máximo 500 caracteres")
    private String value;

    @Schema(description = "是否可见", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "Informe se é visível")
    private Boolean visible;

    @Schema(description = "备注", example = "备注一下很帅气！")
    private String remark;

}
