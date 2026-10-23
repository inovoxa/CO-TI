package cn.iocoder.yudao.module.system.controller.admin.sms.vo.template;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Schema(description = "管理后台 - 短信模板的发送 Request VO")
@Data
public class SmsTemplateSendReqVO {

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15601691300")
    @NotNull(message = "Informe o celular")
    private String mobile;

    @Schema(description = "模板编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "test_01")
    @NotNull(message = "Informe o código do modelo")
    private String templateCode;

    @Schema(description = "模板参数")
    private Map<String, Object> templateParams;

}
