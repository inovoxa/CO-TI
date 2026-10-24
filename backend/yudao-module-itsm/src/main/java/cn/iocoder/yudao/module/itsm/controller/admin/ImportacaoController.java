package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.itsm.service.importacao.ImportacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Importação do GLPI")
@RestController
@RequestMapping("/itsm/importacao")
@Validated
public class ImportacaoController {

    private static final String P = "@ss.hasPermission('itsm:importacao:update')";

    @Resource
    private ImportacaoService importacaoService;

    @PostMapping("/verificar")
    @Operation(summary = "Conecta ao banco do GLPI e mostra o que seria importado (nada é gravado)")
    @PreAuthorize(P)
    public CommonResult<ImportacaoVOs.Verificacao> verificar(@Valid @RequestBody ImportacaoVOs.IniciarReq req) {
        return success(importacaoService.verificar(req));
    }

    @PostMapping("/iniciar")
    @Operation(summary = "Começa a importação em segundo plano")
    @PreAuthorize(P)
    public CommonResult<Long> iniciar(@Valid @RequestBody ImportacaoVOs.IniciarReq req) {
        return success(importacaoService.iniciar(req, getLoginUserId()));
    }

    @GetMapping("/list")
    @PreAuthorize(P)
    public CommonResult<List<ImportacaoVOs.Resp>> listar() {
        return success(importacaoService.listar());
    }

    @GetMapping("/get")
    @Operation(summary = "Andamento e registro de uma importação")
    @PreAuthorize(P)
    public CommonResult<ImportacaoVOs.Resp> obter(@RequestParam("id") Long id) {
        return success(importacaoService.obter(id));
    }

    @PostMapping("/cancelar")
    @PreAuthorize(P)
    public CommonResult<Boolean> cancelar(@RequestParam("id") Long id) {
        importacaoService.cancelar(id);
        return success(true);
    }

}
