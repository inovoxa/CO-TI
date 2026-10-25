package cn.iocoder.yudao.module.monitoramento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.KumaVOs;
import cn.iocoder.yudao.module.monitoramento.service.ImportadorKumaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Monitoramento: importar do Uptime Kuma")
@RestController
@RequestMapping("/monitoramento/importar")
@Validated
public class ImportarKumaController {

    @Resource
    private ImportadorKumaService importadorKumaService;

    /** Arquivo em fluxo (corpo bruto): o kuma.db pode passar de 1 GB com o histórico. */
    @PutMapping(value = "/kuma", consumes = MediaType.ALL_VALUE)
    @Operation(summary = "Enviar o kuma.db e ver o que será importado (nada é gravado ainda)")
    @PreAuthorize("@ss.hasPermission('monitoramento:importar')")
    public CommonResult<KumaVOs.Analise> analisar(HttpServletRequest request) throws IOException {
        return success(importadorKumaService.analisar(request.getInputStream(), getLoginUserId()));
    }

    @PostMapping("/kuma/confirmar")
    @Operation(summary = "Importar o que foi analisado")
    @PreAuthorize("@ss.hasPermission('monitoramento:importar')")
    public CommonResult<KumaVOs.Resultado> confirmar(@Valid @RequestBody KumaVOs.ConfirmarReq req) {
        return success(importadorKumaService.confirmar(req, getLoginUserId()));
    }

}
