package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "CO-TI - segunda etapa do login (código do autenticador ou de recuperação)")
@Data
public class AuthMfaLoginReqVO {

    @Schema(description = "Desafio devolvido pela primeira etapa do login", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Desafio obrigatório")
    @Pattern(regexp = "^[a-f0-9]{32}$", message = "Desafio inválido")
    private String desafio;

    @Schema(description = "Código de 6 dígitos ou código de recuperação", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "123456")
    @NotEmpty(message = "Código obrigatório")
    @Pattern(regexp = "^[0-9A-Za-z-]{6,20}$", message = "Código inválido")
    private String codigo;

}
