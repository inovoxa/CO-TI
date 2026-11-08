package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.itsm.service.PainelService;
import cn.iocoder.yudao.module.itsm.service.RelatorioItsmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Painel do Service Desk")
@RestController
@RequestMapping("/itsm/painel")
@Validated
public class PainelController {

    @Resource
    private PainelService painelService;
    @Resource
    private RelatorioItsmService relatorioItsmService;

    @GetMapping
    @Operation(summary = "Indicadores do período (respeita o escopo de unidade do perfil)")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<Map<String, Object>> painel(@RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                                    @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                                    @RequestParam(value = "deptId", required = false) Long deptId,
                                                    @RequestParam(value = "categoriaId", required = false) Long categoriaId) {
        return success(painelService.painel(de, ate, deptId, categoriaId));
    }

    @GetMapping("/exportar-excel")
    @Operation(summary = "Indicadores do período em planilha")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:exportar')")
    public void exportarExcel(@RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                              @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                              @RequestParam(value = "deptId", required = false) Long deptId,
                              @RequestParam(value = "categoriaId", required = false) Long categoriaId,
                              HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "painel-service-desk.xlsx",
                relatorioItsmService.painel(de, ate, deptId, categoriaId, getLoginUserId()));
    }

}
