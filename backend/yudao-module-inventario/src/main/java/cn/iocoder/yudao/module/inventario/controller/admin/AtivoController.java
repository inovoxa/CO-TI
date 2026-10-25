package cn.iocoder.yudao.module.inventario.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AtivoVOs;
import cn.iocoder.yudao.module.inventario.service.AlertaInventarioService;
import cn.iocoder.yudao.module.inventario.service.AtivoService;
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

@Tag(name = "CO-TI - Inventário: ativos")
@RestController
@RequestMapping("/inventario/ativo")
@Validated
public class AtivoController {

    @Resource
    private AtivoService ativoService;
    @Resource
    private AlertaInventarioService alertaInventarioService;
    @Resource
    private LicencaInventarioService licencaInventarioService;

    @GetMapping("/page")
    @Operation(summary = "Lista de ativos (filtros e alertas)")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<PageResult<AtivoVOs.Resp>> pagina(@Valid AtivoVOs.PaginaReq req) {
        return success(ativoService.pagina(req));
    }

    @GetMapping("/opcoes")
    @Operation(summary = "Sistemas, fabricantes e modelos existentes (para os filtros)")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<AtivoVOs.Opcoes> opcoes() {
        return success(ativoService.opcoes());
    }

    @GetMapping("/contagens")
    @Operation(summary = "Totais e alertas (sem suporte, antivírus, sem contato)")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<Map<String, Long>> contagens() {
        return success(ativoService.contagens());
    }

    @GetMapping("/get")
    @Operation(summary = "Ativo com componentes, softwares e chamados")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<AtivoVOs.Detalhe> detalhe(@RequestParam("id") Long id) {
        AtivoVOs.Detalhe d = ativoService.detalhe(id);
        d.setAlertas(alertaInventarioService.doAtivo(id));
        return success(d);
    }

    @GetMapping("/historico")
    @Operation(summary = "O que mudou no ativo")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<PageResult<AtivoVOs.Historico>> historico(@RequestParam("id") Long id, @Valid PageParam pagina) {
        return success(ativoService.historico(id, pagina));
    }

    @GetMapping("/do-usuario")
    @Operation(summary = "Ativos de uma pessoa")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<List<AtivoVOs.Resp>> doUsuario(@RequestParam("usuarioId") Long usuarioId) {
        return success(ativoService.doUsuario(usuarioId));
    }

    @PostMapping("/create")
    @Operation(summary = "Cadastrar ativo sem agente")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Long> criar(@Valid @RequestBody AtivoVOs.CriarReq req) {
        return success(ativoService.criar(req, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "Situação, patrimônio, unidade e observação (e os demais campos em ativo manual)")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Boolean> atualizar(@Valid @RequestBody AtivoVOs.AtualizarReq req) {
        ativoService.atualizar(req, getLoginUserId());
        return success(true);
    }

    @GetMapping("/software/page")
    @Operation(summary = "Catálogo de softwares com o número de instalações")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<PageResult<AtivoVOs.SoftwareResp>> softwares(@RequestParam(value = "busca", required = false) String busca,
                                                                      @Valid PageParam pagina) {
        PageResult<AtivoVOs.SoftwareResp> p = ativoService.softwares(busca, pagina);
        Map<Long, List<String>> licencas = licencaInventarioService.licencasDosSoftwares(
                p.getList().stream().map(AtivoVOs.SoftwareResp::getId).toList());
        p.getList().forEach(s -> s.setLicencas(licencas.getOrDefault(s.getId(), List.of())));
        return success(p);
    }

    @GetMapping("/agentes")
    @Operation(summary = "Como apontar os agentes e quem está reportando")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<AtivoVOs.PainelAgentes> agentes() {
        return success(ativoService.painel());
    }

    // ---------------------------------------------------------------- chamados

    @GetMapping("/do-chamado")
    @Operation(summary = "Ativos ligados a um chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<List<AtivoVOs.Resp>> doChamado(@RequestParam("chamadoId") Long chamadoId) {
        return success(ativoService.doChamado(chamadoId, getLoginUserId()));
    }

    @PostMapping("/vincular")
    @Operation(summary = "Ligar ativo a chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:update')")
    public CommonResult<Boolean> vincular(@Valid @RequestBody AtivoVOs.VinculoReq req) {
        ativoService.vincular(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/desvincular")
    @Operation(summary = "Desligar ativo do chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:update')")
    public CommonResult<Boolean> desvincular(@Valid @RequestBody AtivoVOs.VinculoReq req) {
        ativoService.desvincular(req, getLoginUserId());
        return success(true);
    }

}
