package cn.iocoder.yudao.module.inventario.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AlertaVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.LicencaInventarioVOs;
import cn.iocoder.yudao.module.inventario.service.AlertaInventarioService;
import cn.iocoder.yudao.module.inventario.service.LicencaInventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Inventário: alertas e licenças")
@RestController
@RequestMapping("/inventario")
@Validated
public class AlertaInventarioController {

    @Resource
    private AlertaInventarioService alertaInventarioService;
    @Resource
    private LicencaInventarioService licencaInventarioService;

    @GetMapping("/alerta/regras")
    @Operation(summary = "Regras de alerta com o número de alertas abertos")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<List<AlertaVOs.Regra>> regras() {
        return success(alertaInventarioService.regras());
    }

    @PutMapping("/alerta/regra")
    @Operation(summary = "Ligar/desligar, limite e abertura de chamado")
    @PreAuthorize("@ss.hasPermission('inventario:alerta:update')")
    public CommonResult<Boolean> salvarRegra(@Valid @RequestBody AlertaVOs.Regra req) {
        alertaInventarioService.salvarRegra(req);
        return success(true);
    }

    @GetMapping("/alerta/page")
    @Operation(summary = "Alertas abertos ou encerrados")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<PageResult<AlertaVOs.Resp>> pagina(@Valid AlertaVOs.PaginaReq req) {
        return success(alertaInventarioService.pagina(req));
    }

    @GetMapping("/alerta/do-ativo")
    @Operation(summary = "Alertas abertos de um ativo")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<List<AlertaVOs.Resp>> doAtivo(@RequestParam("ativoId") Long ativoId) {
        return success(alertaInventarioService.doAtivo(ativoId));
    }

    @PostMapping("/alerta/avaliar")
    @Operation(summary = "Reavaliar os alertas agora")
    @PreAuthorize("@ss.hasPermission('inventario:alerta:update')")
    public CommonResult<AlertaVOs.Avaliacao> avaliar() {
        return success(alertaInventarioService.avaliar());
    }

    // ---------------------------------------------------------------- licenças

    @GetMapping("/licenca/conformidade")
    @Operation(summary = "Licença comparada com as instalações encontradas")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:query')")
    public CommonResult<LicencaInventarioVOs.Conformidade> conformidade(@RequestParam("licencaId") Long licencaId) {
        return success(licencaInventarioService.conformidade(licencaId));
    }

    @GetMapping("/licenca/instalacoes")
    @Operation(summary = "Máquinas encontradas por licença (coluna da lista de licenças)")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:query')")
    public CommonResult<Map<Long, Long>> instalacoes() {
        return success(licencaInventarioService.instalacoesPorLicenca());
    }

    @PostMapping("/licenca/software/vincular")
    @Operation(summary = "Contar um software do catálogo como uso da licença")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:update')")
    public CommonResult<Boolean> vincular(@Valid @RequestBody LicencaInventarioVOs.VinculoReq req) {
        licencaInventarioService.vincular(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/licenca/software/desvincular")
    @Operation(summary = "Deixar de contar o software na licença")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:update')")
    public CommonResult<Boolean> desvincular(@Valid @RequestBody LicencaInventarioVOs.VinculoReq req) {
        licencaInventarioService.desvincular(req);
        return success(true);
    }

}
