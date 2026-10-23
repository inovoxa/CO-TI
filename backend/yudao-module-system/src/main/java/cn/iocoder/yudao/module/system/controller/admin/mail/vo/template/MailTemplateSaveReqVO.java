package cn.iocoder.yudao.module.system.controller.admin.mail.vo.template;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 邮件模版创建/修改 Request VO")
@Data
public class MailTemplateSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "模版名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试名字")
    @NotNull(message = "Informe o nome")
    private String name;

    @Schema(description = "模版编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    @NotNull(message = "Informe o nº do modelo")
    private String code;

    @Schema(description = "发送的邮箱账号编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe a conta de e-mail")
    private Long accountId;

    @Schema(description = "发送人名称", example = "芋头")
    private String nickname;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "注册成功")
    @NotEmpty(message = "Informe o título")
    private String title;

    @Schema(description = "内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好，注册成功啦")
    @NotEmpty(message = "Informe o conteúdo")
    private String content;

    @Schema(description = "状态，参见 CommonStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe a situação")
    private Integer status;

    @Schema(description = "备注", example = "奥特曼")
    private String remark;

}
