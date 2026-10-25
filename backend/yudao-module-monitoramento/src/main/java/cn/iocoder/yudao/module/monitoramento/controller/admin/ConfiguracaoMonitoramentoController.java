package cn.iocoder.yudao.module.monitoramento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.UnidadeVOs;
import cn.iocoder.yudao.module.monitoramento.service.CanalService;
import cn.iocoder.yudao.module.monitoramento.service.ManutencaoService;
import cn.iocoder.yudao.module.monitoramento.service.UnidadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Manutenções, canais de aviso e unidades (com endereço e mapa).
 */
@Tag(name = "CO-TI - Monitoramento: manutenções, canais e unidades")
@RestController
@RequestMapping("/monitoramento")
@Validated
public class ConfiguracaoMonitoramentoController {

    @Resource
    private ManutencaoService manutencaoService;
    @Resource
    private CanalService canalService;
    @Resource
    private UnidadeService unidadeService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    // ================================================================================== manutenções

    @GetMapping("/manutencao/list")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<MonitorVOs.Manutencao>> manutencoes() {
        return success(manutencaoService.listar());
    }

    @PostMapping("/manutencao/save")
    @PreAuthorize("@ss.hasPermission('monitoramento:manutencao:update')")
    public CommonResult<Long> salvarManutencao(@Valid @RequestBody MonitorVOs.Manutencao req) {
        return success(manutencaoService.salvar(req));
    }

    @PostMapping("/manutencao/ativar")
    @Operation(summary = "Ligar ou desligar (a manual vale enquanto ligada)")
    @PreAuthorize("@ss.hasPermission('monitoramento:manutencao:update')")
    public CommonResult<Boolean> ativarManutencao(@RequestParam("id") Long id, @RequestParam("ativa") boolean ativa) {
        manutencaoService.ativar(id, ativa);
        return success(true);
    }

    @DeleteMapping("/manutencao/delete")
    @PreAuthorize("@ss.hasPermission('monitoramento:manutencao:update')")
    public CommonResult<Boolean> excluirManutencao(@RequestParam("id") Long id) {
        manutencaoService.excluir(id);
        return success(true);
    }

    // ================================================================================== canais

    @GetMapping("/canal/list")
    @Operation(summary = "Canais de aviso (a configuração só aparece para quem pode configurar)")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<MonitorVOs.Canal>> canais() {
        List<MonitorVOs.Canal> lista = canalService.listar();
        if (!securityFrameworkService.hasPermission("monitoramento:canal:update")) {
            lista.forEach(c -> c.setConfig(null));
        }
        return success(lista);
    }

    @PostMapping("/canal/save")
    @PreAuthorize("@ss.hasPermission('monitoramento:canal:update')")
    public CommonResult<Long> salvarCanal(@Valid @RequestBody MonitorVOs.Canal req) {
        return success(canalService.salvar(req));
    }

    @PostMapping("/canal/testar")
    @Operation(summary = "Mandar uma mensagem de teste; devolve o erro, se houver")
    @PreAuthorize("@ss.hasPermission('monitoramento:canal:update')")
    public CommonResult<String> testarCanal(@RequestParam("id") Long id) {
        return success(canalService.testar(id));
    }

    @DeleteMapping("/canal/delete")
    @PreAuthorize("@ss.hasPermission('monitoramento:canal:update')")
    public CommonResult<Boolean> excluirCanal(@RequestParam("id") Long id) {
        canalService.excluir(id);
        return success(true);
    }

    // ================================================================================== unidades

    @GetMapping("/unidade/list")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<UnidadeVOs.Unidade>> unidades() {
        return success(unidadeService.listar());
    }

    @PostMapping("/unidade/save")
    @PreAuthorize("@ss.hasPermission('monitoramento:unidade:update')")
    public CommonResult<Long> salvarUnidade(@Valid @RequestBody UnidadeVOs.Unidade req) {
        return success(unidadeService.salvar(req));
    }

    @DeleteMapping("/unidade/delete")
    @PreAuthorize("@ss.hasPermission('monitoramento:unidade:update')")
    public CommonResult<Boolean> excluirUnidade(@RequestParam("id") Long id) {
        unidadeService.excluir(id);
        return success(true);
    }

    @GetMapping("/unidade/cep")
    @Operation(summary = "Endereço do CEP (ViaCEP)")
    @PreAuthorize("@ss.hasPermission('monitoramento:unidade:update')")
    public CommonResult<UnidadeVOs.Endereco> cep(@RequestParam("cep") String cep) {
        return success(unidadeService.buscarCep(cep));
    }

    @PostMapping("/unidade/geocodificar")
    @Operation(summary = "Coordenadas do endereço (OpenStreetMap)")
    @PreAuthorize("@ss.hasPermission('monitoramento:unidade:update')")
    public CommonResult<UnidadeVOs.Coordenadas> geocodificar(@RequestBody UnidadeVOs.GeocodificarReq req) {
        return success(unidadeService.geocodificar(req));
    }

}
