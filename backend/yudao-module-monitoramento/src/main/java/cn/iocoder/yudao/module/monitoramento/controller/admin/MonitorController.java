package cn.iocoder.yudao.module.monitoramento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.service.ImportacaoMonitorService;
import cn.iocoder.yudao.module.monitoramento.service.MonitorService;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Monitoramento: monitores")
@RestController
@RequestMapping("/monitoramento/monitor")
@Validated
public class MonitorController {

    @Resource
    private MonitorService monitorService;
    @Resource
    private ImportacaoMonitorService importacaoMonitorService;

    @GetMapping("/page")
    @Operation(summary = "Monitores com estado, últimas batidas e disponibilidade")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<PageResult<MonitorVOs.Resp>> pagina(@Valid MonitorVOs.PaginaReq req) {
        return success(monitorService.pagina(req));
    }

    @GetMapping("/exportar")
    @Operation(summary = "Planilha com tudo o que o filtro alcança")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public void exportar(@Valid MonitorVOs.PaginaReq req, HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "monitores.xlsx", monitorService.planilha(req));
    }

    @PostMapping("/importar/analisar")
    @Operation(summary = "Prévia da planilha: o que será adicionado, atualizado e removido, sem gravar nada")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<ImportacaoVOs.Previa> analisarImportacao(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(importacaoMonitorService.analisar(entrada));
        }
    }

    @PostMapping("/importar")
    @Operation(summary = "Aplicar a planilha; só remove os monitores ausentes indicados em removerIds")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<ImportacaoVOs.Resultado> importar(@RequestParam("arquivo") MultipartFile arquivo,
                                                          @RequestParam(value = "removerIds", required = false) List<Long> removerIds) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(importacaoMonitorService.aplicar(entrada, removerIds, getLoginUserId()));
        }
    }

    @GetMapping("/contagens")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<MonitorVOs.Contagens> contagens() {
        return success(monitorService.contagens());
    }

    @GetMapping("/simples")
    @Operation(summary = "Id e nome de todos os monitores (seletores)")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<Map<String, Object>>> simples() {
        return success(monitorService.todos().stream().map(m -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", m.getId());
            r.put("nome", m.getNome());
            r.put("unidadeId", m.getUnidadeId());
            return r;
        }).toList());
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<MonitorVOs.Detalhe> obter(@RequestParam("id") Long id) {
        return success(monitorService.detalhe(id));
    }

    @GetMapping("/serie")
    @Operation(summary = "Latência e falhas agrupadas (3h, 24h, 7d ou 30d)")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<MonitorVOs.Ponto>> serie(@RequestParam("id") Long id, @RequestParam(value = "periodo", required = false) String periodo) {
        return success(monitorService.serie(id, periodo));
    }

    @GetMapping("/eventos")
    @Operation(summary = "Mudanças de estado recentes de todos os monitores visíveis")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<MonitorVOs.Evento>> eventos(@RequestParam(value = "dias", defaultValue = "7") int dias) {
        List<MonitorDO> todos = monitorService.todos();
        return success(monitorService.eventos(todos, LocalDateTime.now().minusDays(Math.max(1, Math.min(dias, 90))), 100));
    }

    @PostMapping("/save")
    @Operation(summary = "Criar ou alterar monitor")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Long> salvar(@Valid @RequestBody MonitorVOs.Monitor req) {
        return success(monitorService.salvar(req));
    }

    @PostMapping("/testar")
    @Operation(summary = "Uma verificação agora com a configuração informada, sem salvar")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<MonitorVOs.Teste> testar(@Valid @RequestBody MonitorVOs.Monitor req) {
        return success(monitorService.testar(req));
    }

    @PostMapping("/ativar")
    @Operation(summary = "Retomar ou pausar")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Boolean> ativar(@RequestParam("id") Long id, @RequestParam("ativo") boolean ativo) {
        monitorService.ativar(id, ativo);
        return success(true);
    }

    @PostMapping("/verificar")
    @Operation(summary = "Verificar agora, sem esperar o intervalo")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Boolean> verificar(@RequestParam("id") Long id) {
        monitorService.verificarAgora(id);
        return success(true);
    }

    @PostMapping("/manual")
    @Operation(summary = "Estado escolhido à mão (monitor manual)")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Boolean> manual(@RequestParam("id") Long id, @RequestParam("noAr") boolean noAr,
                                        @RequestParam(value = "msg", required = false) String msg) {
        monitorService.marcarManual(id, noAr, msg);
        return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Boolean> excluir(@RequestParam("id") Long id) {
        monitorService.excluir(id);
        return success(true);
    }

}
