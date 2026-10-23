package cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "管理后台 - 部门创建/修改 Request VO")
@Data
public class DeptSaveReqVO {

    @Schema(description = "部门编号", example = "1024")
    private Long id;

    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道")
    @NotBlank(message = "Informe o nome do departamento")
    @Size(max = 30, message = "O nome do departamento pode ter no máximo 30 caracteres")
    private String name;

    @Schema(description = "父部门 ID", example = "1024")
    private Long parentId;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "Informe a ordem")
    private Integer sort;

    @Schema(description = "负责人的用户编号", example = "2048")
    private Long leaderUserId;

    @Schema(description = "联系电话", example = "15601691000")
    @Size(max = 11, message = "Telefone inválido")
    private String phone;

    @Schema(description = "邮箱", example = "yudao@iocoder.cn")
    @Email(message = "E-mail inválido")
    @Size(max = 50, message = "O e-mail pode ter no máximo 128 caracteres")
    private String email;

    @Schema(description = "状态,见 CommonStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe a situação")
    @InEnum(value = CommonStatusEnum.class, message = "A situação deve ser {value}")
    private Integer status;

}
