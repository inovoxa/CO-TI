package cn.iocoder.yudao.module.monitoramento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.KumaVOs;
import cn.iocoder.yudao.module.monitoramento.service.KumaServidorService;
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

@Tag(name = "CO-TI - Monitoramento: Uptime Kuma ao vivo")
@RestController
@RequestMapping("/monitoramento/kuma-servidor")
@Validated
public class KumaServidorController {

    @Resource
    private KumaServidorService kumaServidorService;

    @GetMapping("/list")
    @Operation(summary = "Servidores Uptime Kuma acompanhados (a chave só volta como definida)")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:query')")
    public CommonResult<List<KumaVOs.Servidor>> listar() {
        return success(kumaServidorService.listar());
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Long> salvar(@Valid @RequestBody KumaVOs.Servidor req) {
        return success(kumaServidorService.salvar(req));
    }

    @PostMapping("/testar")
    @Operation(summary = "Lê o /metrics sem salvar")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<String> testar(@Valid @RequestBody KumaVOs.Servidor req) {
        return success(kumaServidorService.testar(req));
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<Boolean> excluir(@RequestParam("id") Long id) {
        kumaServidorService.excluir(id);
        return success(true);
    }

    @GetMapping("/previa")
    @Operation(summary = "Monitores do Kuma e o que o CO-TI já acompanha (nada é gravado)")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<KumaVOs.PreviaAoVivo> previa(@RequestParam("id") Long id) {
        return success(kumaServidorService.previa(id));
    }

    @PostMapping("/sincronizar")
    @Operation(summary = "Acompanhar, atualizar ou deixar de acompanhar monitores do Kuma")
    @PreAuthorize("@ss.hasPermission('monitoramento:monitor:update')")
    public CommonResult<KumaVOs.SincronizarResultado> sincronizar(@Valid @RequestBody KumaVOs.SincronizarReq req) {
        return success(kumaServidorService.sincronizar(req, getLoginUserId()));
    }

}
