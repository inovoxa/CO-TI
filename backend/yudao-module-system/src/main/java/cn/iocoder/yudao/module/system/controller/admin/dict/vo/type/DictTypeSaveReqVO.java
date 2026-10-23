package cn.iocoder.yudao.module.system.controller.admin.dict.vo.type;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "管理后台 - 字典类型创建/修改 Request VO")
@Data
public class DictTypeSaveReqVO {

    @Schema(description = "字典类型编号", example = "1024")
    private Long id;

    @Schema(description = "字典名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "性别")
    @NotBlank(message = "Informe o nome do dicionário")
    @Size(max = 100, message = "O nome pode ter no máximo 100 caracteres")
    private String name;

    @Schema(description = "字典类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys_common_sex")
    @NotNull(message = "Informe o tipo do dicionário")
    @Size(max = 100, message = "O tipo pode ter no máximo 100 caracteres")
    private String type;

    @Schema(description = "状态，参见 CommonStatusEnum 枚举类", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe a situação")
    private Integer status;

    @Schema(description = "备注", example = "快乐的备注")
    private String remark;

}
