package cn.iocoder.yudao.module.monitoramento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.UnidadeVOs;
import cn.iocoder.yudao.module.monitoramento.service.CanalService;
import cn.iocoder.yudao.module.monitoramento.service.ImportacaoUnidadeService;
import cn.iocoder.yudao.module.monitoramento.service.ManutencaoService;
import cn.iocoder.yudao.module.monitoramento.service.UnidadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

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
    private ImportacaoUnidadeService importacaoUnidadeService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private cn.iocoder.yudao.module.monitoramento.service.DockerHostService dockerHostService;

    // ================================================================================== servidores Docker

    @GetMapping("/docker-host/list")
    @Operation(summary = "Servidores Docker (a chave do cliente só volta como definida)")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<MonitorVOs.DockerHost>> dockerHosts() {
        return success(dockerHostService.listar());
    }

    @PostMapping("/docker-host/save")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Long> salvarDockerHost(@Valid @RequestBody MonitorVOs.DockerHost req) {
        return success(dockerHostService.salvar(req));
    }

    @PostMapping("/docker-host/testar")
    @Operation(summary = "Testa a conexão sem salvar; devolve a versão do Docker")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<String> testarDockerHost(@Valid @RequestBody MonitorVOs.DockerHost req) {
        return success(dockerHostService.testar(req));
    }

    @DeleteMapping("/docker-host/delete")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Boolean> excluirDockerHost(@RequestParam("id") Long id) {
        dockerHostService.excluir(id);
        return success(true);
    }

    // ================================================================================== manutenções

    @GetMapping("/manutencao/list")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<MonitorVOs.Manutencao>> manutencoes() {
        return success(manutencaoService.listar());
    }

    @GetMapping("/manutencao/exportar")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public void exportarManutencoes(HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "manutencoes.xlsx", manutencaoService.planilha());
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

    @GetMapping("/canal/exportar")
    @Operation(summary = "Planilha dos canais, sem configuração nem segredos")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public void exportarCanais(HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "canais-de-aviso.xlsx", canalService.planilha());
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

    @GetMapping("/unidade/exportar")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public void exportarUnidades(HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "unidades.xlsx", unidadeService.planilha());
    }

    @PostMapping("/unidade/importar/analisar")
    @Operation(summary = "Prévia da planilha: o que será adicionado, atualizado e removido, sem gravar nada")
    @PreAuthorize("@ss.hasPermission('monitoramento:unidade:update')")
    public CommonResult<ImportacaoVOs.Previa> analisarImportacaoUnidades(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(importacaoUnidadeService.analisar(entrada));
        }
    }

    @PostMapping("/unidade/importar")
    @Operation(summary = "Aplicar a planilha; só remove as unidades ausentes indicadas em removerIds")
    @PreAuthorize("@ss.hasPermission('monitoramento:unidade:update')")
    public CommonResult<ImportacaoVOs.Resultado> importarUnidades(@RequestParam("arquivo") MultipartFile arquivo,
                                                                  @RequestParam(value = "removerIds", required = false) List<Long> removerIds) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(importacaoUnidadeService.aplicar(entrada, removerIds, getLoginUserId()));
        }
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
