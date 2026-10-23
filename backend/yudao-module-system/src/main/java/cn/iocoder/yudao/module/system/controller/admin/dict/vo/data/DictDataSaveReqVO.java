package cn.iocoder.yudao.module.system.controller.admin.dict.vo.data;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "管理后台 - 字典数据创建/修改 Request VO")
@Data
public class DictDataSaveReqVO {

    @Schema(description = "字典数据编号", example = "1024")
    private Long id;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "Informe a ordem")
    private Integer sort;

    @Schema(description = "字典标签", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道")
    @NotBlank(message = "Informe o rótulo")
    @Size(max = 100, message = "O rótulo pode ter no máximo 100 caracteres")
    private String label;

    @Schema(description = "字典值", requiredMode = Schema.RequiredMode.REQUIRED, example = "iocoder")
    @NotBlank(message = "Informe o valor")
    @Size(max = 100, message = "O valor pode ter no máximo 100 caracteres")
    private String value;

    @Schema(description = "字典类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys_common_sex")
    @NotBlank(message = "Informe o tipo do dicionário")
    @Size(max = 100, message = "O tipo pode ter no máximo 100 caracteres")
    private String dictType;

    @Schema(description = "状态,见 CommonStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe a situação")
    @InEnum(value = CommonStatusEnum.class, message = "A situação deve ser {value}")
    private Integer status;

    @Schema(description = "颜色类型,default、primary、success、info、warning、danger", example = "default")
    private String colorType;

    @Schema(description = "css 样式", example = "btn-visible")
    private String cssClass;

    @Schema(description = "备注", example = "我是一个角色")
    private String remark;

}
