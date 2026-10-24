package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.itsm.service.PainelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CO-TI - Painel do Service Desk")
@RestController
@RequestMapping("/itsm/painel")
@Validated
public class PainelController {

    @Resource
    private PainelService painelService;

    @GetMapping
    @Operation(summary = "Indicadores do período (respeita o escopo de unidade do perfil)")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<Map<String, Object>> painel(@RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                                    @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                                    @RequestParam(value = "deptId", required = false) Long deptId,
                                                    @RequestParam(value = "categoriaId", required = false) Long categoriaId) {
        return success(painelService.painel(de, ate, deptId, categoriaId));
    }

}
