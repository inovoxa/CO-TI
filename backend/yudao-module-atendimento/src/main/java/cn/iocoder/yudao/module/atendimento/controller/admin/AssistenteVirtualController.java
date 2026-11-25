package cn.iocoder.yudao.module.atendimento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AssistenteVirtualVOs;
import cn.iocoder.yudao.module.atendimento.service.AssistenteVirtualService;
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

@Tag(name = "CO-TI - Atendimento - Assistente virtual")
@RestController
@RequestMapping("/atendimento/assistente")
@Validated
public class AssistenteVirtualController {

    private static final String P = "@ss.hasPermission('atendimento:assistente:config')";

    @Resource
    private AssistenteVirtualService service;

    @GetMapping("/get")
    @Operation(summary = "Textos, opções e caixas do assistente virtual")
    @PreAuthorize(P)
    public CommonResult<AssistenteVirtualVOs.Tela> tela() {
        return success(service.tela());
    }

    @PostMapping("/textos")
    @Operation(summary = "Gravar textos e opções (vazio ou igual ao padrão volta ao padrão)")
    @PreAuthorize(P)
    public CommonResult<Boolean> salvar(@Valid @RequestBody AssistenteVirtualVOs.SalvarTextos req) {
        service.salvar(req.getValores(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/caixa")
    @Operation(summary = "Robô de uma caixa: fluxo do catálogo, assistente de IA e avisos")
    @PreAuthorize(P)
    public CommonResult<Boolean> salvarCaixa(@Valid @RequestBody AssistenteVirtualVOs.CaixaRobo req) {
        service.salvarCaixa(req, getLoginUserId());
        return success(true);
    }

    @GetMapping("/exportar")
    @Operation(summary = "Textos e opções em JSON (sem segredos)")
    @PreAuthorize(P)
    public CommonResult<AssistenteVirtualVOs.Arquivo> exportar() {
        return success(service.exportar());
    }

    @PostMapping("/importar/previa")
    @Operation(summary = "O que a importação muda, sem gravar")
    @PreAuthorize(P)
    public CommonResult<List<AssistenteVirtualVOs.LinhaImportacao>> previaImportacao(@RequestBody AssistenteVirtualVOs.Arquivo arq) {
        return success(service.previaImportacao(arq));
    }

    @PostMapping("/importar")
    @Operation(summary = "Aplicar a importação (só o que muda; inválidos ficam de fora)")
    @PreAuthorize(P)
    public CommonResult<Map<String, Integer>> importar(@RequestBody AssistenteVirtualVOs.Arquivo arq) {
        return success(Map.of("alterados", service.importar(arq, getLoginUserId())));
    }

}
