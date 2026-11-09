package cn.iocoder.yudao.module.inventario.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.RedeVOs;
import cn.iocoder.yudao.module.inventario.service.RedeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Inventário: descoberta de rede")
@RestController
@RequestMapping("/inventario/rede")
@Validated
public class RedeController {

    @Resource
    private RedeService redeService;

    @GetMapping("/subredes")
    @Operation(summary = "Sub-redes varridas, com quantos equipamentos e quem varre")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<List<RedeVOs.Subrede>> subredes() {
        return success(redeService.subredes());
    }

    @GetMapping("/page")
    @Operation(summary = "Equipamentos que apareceram na rede")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<PageResult<RedeVOs.Dispositivo>> pagina(@Valid RedeVOs.PaginaReq req) {
        return success(redeService.pagina(req));
    }

    @GetMapping("/exportar")
    @Operation(summary = "Planilha com os equipamentos do filtro e as sub-redes")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public void exportar(@Valid RedeVOs.PaginaReq req, HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "rede.xlsx", redeService.planilha(req));
    }

    @PostMapping("/cadastrar")
    @Operation(summary = "Cadastrar o equipamento descoberto como ativo")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Long> cadastrar(@Valid @RequestBody RedeVOs.CadastrarReq req) {
        return success(redeService.cadastrar(req, getLoginUserId()));
    }

    @PostMapping("/ignorar")
    @Operation(summary = "Marcar (ou desmarcar) como conhecido sem cadastro")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Boolean> ignorar(@Valid @RequestBody RedeVOs.IgnorarReq req) {
        redeService.ignorar(req.getId(), req.getIgnorado(), req.getObservacao());
        return success(true);
    }

}
