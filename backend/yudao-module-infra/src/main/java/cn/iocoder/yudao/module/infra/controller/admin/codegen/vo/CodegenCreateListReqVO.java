package cn.iocoder.yudao.module.infra.controller.admin.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 基于数据库的表结构，创建代码生成器的表和字段定义 Request VO")
@Data
public class CodegenCreateListReqVO {

    @Schema(description = "数据源配置的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe a fonte de dados")
    private Long dataSourceConfigId;

    @Schema(description = "表名数组", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1, 2, 3]")
    @NotNull(message = "Informe as tabelas")
    private List<String> tableNames;

}
