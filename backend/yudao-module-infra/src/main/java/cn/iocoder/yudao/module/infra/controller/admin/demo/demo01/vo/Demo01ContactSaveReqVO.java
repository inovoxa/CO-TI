package cn.iocoder.yudao.module.infra.controller.admin.demo.demo01.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 示例联系人新增/修改 Request VO")
@Data
public class Demo01ContactSaveReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "21555")
    private Long id;

    @Schema(description = "名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotEmpty(message = "Informe o nome")
    private String name;

    @Schema(description = "性别", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe o sexo")
    private Integer sex;

    @Schema(description = "出生年", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Informe o ano de nascimento")
    private LocalDateTime birthday;

    @Schema(description = "简介", requiredMode = Schema.RequiredMode.REQUIRED, example = "你说的对")
    @NotEmpty(message = "Informe a descrição")
    private String description;

    @Schema(description = "头像")
    private String avatar;

}