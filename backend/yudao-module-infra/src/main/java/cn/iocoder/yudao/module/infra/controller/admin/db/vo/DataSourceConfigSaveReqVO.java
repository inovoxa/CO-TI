package cn.iocoder.yudao.module.infra.controller.admin.db.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 数据源配置创建/修改 Request VO")
@Data
public class DataSourceConfigSaveReqVO {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    @NotNull(message = "Informe o nome da fonte de dados")
    private String name;

    @Schema(description = "数据源连接", requiredMode = Schema.RequiredMode.REQUIRED, example = "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro")
    @NotNull(message = "Informe a conexão")
    private String url;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "root")
    @NotNull(message = "Informe o usuário")
    private String username;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotNull(message = "Informe a senha")
    private String password;

}
