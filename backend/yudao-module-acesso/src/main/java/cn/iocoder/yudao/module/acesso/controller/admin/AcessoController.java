package cn.iocoder.yudao.module.acesso.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.acesso.controller.admin.vo.AcessoVOs;
import cn.iocoder.yudao.module.acesso.service.AcessoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Acesso rápido")
@RestController
@RequestMapping("/acesso")
@Validated
public class AcessoController {

    private static final String USAR = "@ss.hasPermission('acesso:link:usar')";
    private static final String GERENCIAR = "@ss.hasPermission('acesso:link:gerenciar')";

    @Resource
    private AcessoService acessoService;

    @GetMapping("/painel")
    @Operation(summary = "Links públicos e os meus")
    @PreAuthorize(USAR)
    public CommonResult<AcessoVOs.Painel> painel() {
        return success(acessoService.painel(getLoginUserId()));
    }

    @PostMapping("/publico")
    @Operation(summary = "Cria ou altera um link público")
    @PreAuthorize(GERENCIAR)
    public CommonResult<Long> salvarPublico(@Valid @RequestBody AcessoVOs.Salvar req) {
        return success(acessoService.salvarPublico(req));
    }

    @DeleteMapping("/publico")
    @Operation(summary = "Exclui um link público")
    @PreAuthorize(GERENCIAR)
    public CommonResult<Boolean> excluirPublico(@RequestParam("id") Long id) {
        acessoService.excluirPublico(id);
        return success(true);
    }

    @PostMapping("/meu")
    @Operation(summary = "Cria ou altera um link pessoal; até 10 por pessoa")
    @PreAuthorize(USAR)
    public CommonResult<Long> salvarMeu(@Valid @RequestBody AcessoVOs.Salvar req) {
        return success(acessoService.salvarMeu(req, getLoginUserId()));
    }

    @DeleteMapping("/meu")
    @Operation(summary = "Exclui um link pessoal")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> excluirMeu(@RequestParam("id") Long id) {
        acessoService.excluirMeu(id, getLoginUserId());
        return success(true);
    }

}
