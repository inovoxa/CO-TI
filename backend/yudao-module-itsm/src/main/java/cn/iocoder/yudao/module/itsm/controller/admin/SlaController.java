package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.SlaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.FeriadoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.SlaPrioridadeDO;
import cn.iocoder.yudao.module.itsm.service.AutomacaoSlaJob;
import cn.iocoder.yudao.module.itsm.service.SlaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CO-TI - SLA, calendários e feriados")
@RestController
@RequestMapping("/itsm")
@Validated
public class SlaController {

    @Resource
    private SlaService slaService;
    @Resource
    private AutomacaoSlaJob automacao;

    // -------- SLA --------

    @GetMapping("/sla/list")
    @Operation(summary = "SLAs com os níveis de escalonamento")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:sla:query', 'itsm:categoria:query')")
    public CommonResult<List<SlaVOs.Sla>> slas() {
        return success(slaService.listarSlas());
    }

    @PostMapping("/sla/save")
    @Operation(summary = "Criar ou editar SLA (e seus níveis)")
    @PreAuthorize("@ss.hasPermission('itsm:sla:update')")
    public CommonResult<Long> salvarSla(@Valid @RequestBody SlaVOs.Sla req) {
        return success(slaService.salvarSla(req));
    }

    @DeleteMapping("/sla/delete")
    @Operation(summary = "Excluir SLA")
    @PreAuthorize("@ss.hasPermission('itsm:sla:update')")
    public CommonResult<Boolean> excluirSla(@RequestParam("id") Long id) {
        slaService.excluirSla(id);
        return success(true);
    }

    @GetMapping("/sla/prioridade")
    @Operation(summary = "SLA padrão de cada prioridade")
    @PreAuthorize("@ss.hasPermission('itsm:sla:query')")
    public CommonResult<List<SlaPrioridadeDO>> porPrioridade() {
        return success(slaService.listarPorPrioridade());
    }

    @PutMapping("/sla/prioridade")
    @Operation(summary = "Definir o SLA padrão de cada prioridade")
    @PreAuthorize("@ss.hasPermission('itsm:sla:update')")
    public CommonResult<Boolean> salvarPorPrioridade(@Valid @RequestBody List<SlaVOs.PorPrioridade> lista) {
        slaService.salvarPorPrioridade(lista);
        return success(true);
    }

    @PostMapping("/sla/executar-automacao")
    @Operation(summary = "Roda agora o escalonamento e o fechamento automático (normalmente rodam sozinhos)")
    @PreAuthorize("@ss.hasPermission('itsm:sla:update')")
    public CommonResult<Map<String, Integer>> executarAutomacao() {
        LocalDateTime agora = LocalDateTime.now();
        return success(Map.of("escalonamentos", automacao.escalonarAgora(agora), "fechados", automacao.fecharAgora(agora)));
    }

    // -------- calendários --------

    @GetMapping("/calendario/list")
    @Operation(summary = "Calendários com períodos e feriados")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:calendario:query', 'itsm:sla:query')")
    public CommonResult<List<SlaVOs.Calendario>> calendarios() {
        return success(slaService.listarCalendarios());
    }

    @PostMapping("/calendario/save")
    @Operation(summary = "Criar ou editar calendário")
    @PreAuthorize("@ss.hasPermission('itsm:calendario:update')")
    public CommonResult<Long> salvarCalendario(@Valid @RequestBody SlaVOs.Calendario req) {
        return success(slaService.salvarCalendario(req));
    }

    @DeleteMapping("/calendario/delete")
    @Operation(summary = "Excluir calendário")
    @PreAuthorize("@ss.hasPermission('itsm:calendario:update')")
    public CommonResult<Boolean> excluirCalendario(@RequestParam("id") Long id) {
        slaService.excluirCalendario(id);
        return success(true);
    }

    // -------- feriados --------

    @GetMapping("/feriado/list")
    @Operation(summary = "Feriados")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:calendario:query', 'itsm:sla:query')")
    public CommonResult<List<FeriadoDO>> feriados() {
        return success(slaService.listarFeriados());
    }

    @PostMapping("/feriado/save")
    @Operation(summary = "Criar ou editar feriado")
    @PreAuthorize("@ss.hasPermission('itsm:calendario:update')")
    public CommonResult<Long> salvarFeriado(@Valid @RequestBody SlaVOs.Feriado req) {
        return success(slaService.salvarFeriado(req));
    }

    @DeleteMapping("/feriado/delete")
    @Operation(summary = "Excluir feriado")
    @PreAuthorize("@ss.hasPermission('itsm:calendario:update')")
    public CommonResult<Boolean> excluirFeriado(@RequestParam("id") Long id) {
        slaService.excluirFeriado(id);
        return success(true);
    }

}
