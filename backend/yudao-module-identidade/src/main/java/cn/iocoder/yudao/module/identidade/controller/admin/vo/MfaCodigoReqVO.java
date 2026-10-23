package cn.iocoder.yudao.module.identidade.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "CO-TI - código do autenticador ou de recuperação")
@Data
public class MfaCodigoReqVO {

    @Schema(description = "6 dígitos do aplicativo, ou código de recuperação", example = "123456")
    @NotEmpty(message = "Informe o código")
    @Pattern(regexp = "^[0-9A-Za-z-]{6,20}$", message = "Código inválido")
    private String codigo;

}
