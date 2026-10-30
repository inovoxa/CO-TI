package cn.iocoder.yudao.module.infra.controller.admin.marca;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.infra.service.marca.MarcaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CO-TI - Identidade visual (nome, logo, foto e texto do login da instância)")
@RestController
@RequestMapping("/infra/marca")
@Validated
public class MarcaController {

    @Resource
    private MarcaService marcaService;

    @GetMapping
    @Operation(summary = "Nome, logo, foto e texto do login da instância (público: a tela de login também usa)")
    @PermitAll
    @TenantIgnore
    public CommonResult<Marca> obter() {
        return success(marcaService.obter());
    }

    @PutMapping
    @Operation(summary = "Altera o nome, a logo, a foto e o texto do login da instância")
    @PreAuthorize("@ss.hasPermission('infra:marca:update')")
    public CommonResult<Boolean> salvar(@Valid @RequestBody Marca marca) {
        marcaService.salvar(marca);
        return success(true);
    }

    @Data
    @Accessors(chain = true)
    public static class Marca {

        @Schema(description = "Nome mostrado no menu, na aba do navegador e no login", example = "Centro de Operações de TI")
        @NotBlank(message = "Informe o nome")
        @Size(max = 60, message = "O nome pode ter no máximo 60 caracteres")
        private String nome;

        @Schema(description = "Endereço da logo; vazio usa a logo padrão do CO-TI", example = "/admin-api/infra/file/50000/get/logo.png")
        @Size(max = 500, message = "Endereço da logo muito longo")
        @Pattern(regexp = "^$|^(https?://|/admin-api/infra/file/)\\S+$", message = "Endereço da logo inválido")
        private String logo;

        @Schema(description = "Foto do lado esquerdo da tela de login; vazio usa a padrão", example = "/admin-api/infra/file/50000/get/fundo.jpg")
        @Size(max = 500, message = "Endereço da foto muito longo")
        @Pattern(regexp = "^$|^(https?://|/admin-api/infra/file/)\\S+$", message = "Endereço da foto inválido")
        private String fundo;

        @Schema(description = "Texto abaixo do \"Bem-vindo\" no login; uma frase por linha, vazio usa o padrão",
                example = "Monitoramento da rede, atendimento e chamados de TI em um só lugar.")
        @Size(max = 300, message = "O texto do login pode ter no máximo 300 caracteres")
        private String frase;

    }

}
