package cn.iocoder.yudao.module.infra.controller.admin.file.vo.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 文件配置创建/修改 Request VO")
@Data
public class FileConfigSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "配置名", requiredMode = Schema.RequiredMode.REQUIRED, example = "S3 - 阿里云")
    @NotNull(message = "Informe o nome da configuração")
    private String name;

    @Schema(description = "存储器，参见 FileStorageEnum 枚举类", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe o armazenamento")
    private Integer storage;

    @Schema(description = "存储配置,配置是动态参数，所以使用 Map 接收", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Informe o armazenamento")
    private Map<String, Object> config;

    @Schema(description = "备注", example = "我是备注")
    private String remark;

}
