package cn.iocoder.yudao.module.cofre.controller.admin;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cofre.controller.admin.vo.CofreVOs;
import cn.iocoder.yudao.module.cofre.service.CofreService;
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

/**
 * A permissão abre a tela; quem pode o quê em cada pasta é conferido no serviço, pelo papel da pessoa na pasta.
 * Os pedidos que levam ou trazem senha não entram no log de acesso.
 */
@Tag(name = "CO-TI - Cofre de senhas")
@RestController
@RequestMapping("/cofre")
@Validated
public class CofreController {

    @Resource
    private CofreService cofreService;

    @GetMapping("/resumo")
    @Operation(summary = "Pastas que a pessoa pode ver")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    public CommonResult<CofreVOs.Resumo> resumo() {
        return success(cofreService.resumo(getLoginUserId()));
    }

    @PostMapping("/pasta/salvar")
    @Operation(summary = "Criar ou alterar pasta")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    public CommonResult<Long> salvarPasta(@Valid @RequestBody CofreVOs.PastaReq req) {
        return success(cofreService.salvarPasta(req, getLoginUserId()));
    }

    @DeleteMapping("/pasta/excluir")
    @Operation(summary = "Excluir pasta vazia")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    public CommonResult<Boolean> excluirPasta(@RequestParam("id") Long id) {
        cofreService.excluirPasta(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/pasta/membros")
    @Operation(summary = "Quem tem acesso à pasta")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    public CommonResult<List<CofreVOs.Membro>> membros(@RequestParam("pastaId") Long pastaId) {
        return success(cofreService.membros(pastaId, getLoginUserId()));
    }

    @PostMapping("/pasta/membro")
    @Operation(summary = "Incluir pessoa na pasta ou mudar o papel")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    public CommonResult<Boolean> salvarMembro(@Valid @RequestBody CofreVOs.MembroReq req) {
        cofreService.salvarMembro(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/pasta/membro")
    @Operation(summary = "Tirar pessoa da pasta")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    public CommonResult<Boolean> removerMembro(@RequestParam("pastaId") Long pastaId, @RequestParam("userId") Long userId) {
        cofreService.removerMembro(pastaId, userId, getLoginUserId());
        return success(true);
    }

    @GetMapping("/segredo/lista")
    @Operation(summary = "Senhas da pasta, sem o valor")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    public CommonResult<List<CofreVOs.Segredo>> segredos(@RequestParam(value = "pastaId", required = false) Long pastaId,
                                                         @RequestParam(value = "busca", required = false) String busca) {
        return success(cofreService.segredos(pastaId, busca, getLoginUserId()));
    }

    @PostMapping("/segredo/salvar")
    @Operation(summary = "Cadastrar ou alterar senha")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Long> salvarSegredo(@Valid @RequestBody CofreVOs.SegredoReq req) {
        return success(cofreService.salvarSegredo(req, getLoginUserId()));
    }

    @DeleteMapping("/segredo/excluir")
    @Operation(summary = "Excluir senha")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    public CommonResult<Boolean> excluirSegredo(@RequestParam("id") Long id) {
        cofreService.excluirSegredo(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/segredo/revelar")
    @Operation(summary = "Ver a senha e as notas (fica na trilha de auditoria)")
    @PreAuthorize("@ss.hasPermission('cofre:segredo:usar')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<CofreVOs.Revelado> revelar(@RequestParam("id") Long id) {
        return success(cofreService.revelar(id, getLoginUserId()));
    }

}
