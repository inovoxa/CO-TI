package cn.iocoder.yudao.module.inventario.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AtivoVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.EnvioVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.LimpezaVOs;
import cn.iocoder.yudao.module.inventario.service.EnvioAgenteService;
import cn.iocoder.yudao.module.inventario.service.LimpezaInventarioService;
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

/** Página "Agentes": envios recebidos e limpeza (máquinas repetidas e paradas). */
@Tag(name = "CO-TI - Inventário: envios dos agentes e limpeza")
@RestController
@RequestMapping("/inventario/limpeza")
@Validated
public class InventarioLimpezaController {

    @Resource
    private EnvioAgenteService envioAgenteService;
    @Resource
    private LimpezaInventarioService limpezaInventarioService;

    @GetMapping("/envios")
    @Operation(summary = "Cada pedido dos agentes, aceito ou recusado")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<PageResult<EnvioVOs.Resp>> envios(@Valid EnvioVOs.PaginaReq req) {
        return success(envioAgenteService.pagina(req));
    }

    @GetMapping("/envios/resumo")
    @Operation(summary = "Envios das últimas 24 h por resultado e os IPs recusados")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<EnvioVOs.Resumo> resumo() {
        return success(envioAgenteService.resumo());
    }

    @GetMapping("/envios/exportar")
    @Operation(summary = "Envios em planilha, com os filtros da tela")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:exportar')")
    public void exportar(@Valid EnvioVOs.PaginaReq req, HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "envios-agentes.xlsx", envioAgenteService.exportar(req, getLoginUserId()));
    }

    @GetMapping("/repetidas")
    @Operation(summary = "Ativos que parecem ser a mesma máquina")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<List<LimpezaVOs.Grupo>> repetidas() {
        return success(limpezaInventarioService.repetidas());
    }

    @PostMapping("/mesclar")
    @Operation(summary = "Juntar ativos repetidos num só")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Boolean> mesclar(@Valid @RequestBody LimpezaVOs.MesclarReq req) {
        limpezaInventarioService.mesclar(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/distintos")
    @Operation(summary = "Marcar como máquinas diferentes (não aparecem mais como repetidas)")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Boolean> distintos(@Valid @RequestBody LimpezaVOs.IdsReq req) {
        limpezaInventarioService.distintos(req.getIds(), getLoginUserId());
        return success(true);
    }

    @GetMapping("/paradas")
    @Operation(summary = "Máquinas sem contato há mais de N dias")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<List<AtivoVOs.Resp>> paradas(@Valid LimpezaVOs.ParadasReq req) {
        return success(limpezaInventarioService.paradas(req.getDias()));
    }

    @PostMapping("/baixar")
    @Operation(summary = "Dar baixa (situação baixado)")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Integer> baixar(@Valid @RequestBody LimpezaVOs.IdsReq req) {
        return success(limpezaInventarioService.baixar(req.getIds(), getLoginUserId()));
    }

}
