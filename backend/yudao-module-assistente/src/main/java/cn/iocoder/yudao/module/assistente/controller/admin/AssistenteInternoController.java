package cn.iocoder.yudao.module.assistente.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.UserRateLimiterKeyResolver;
import cn.iocoder.yudao.module.assistente.controller.admin.vo.AssistenteVOs;
import cn.iocoder.yudao.module.assistente.service.AssistenteInternoService;
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

@Tag(name = "CO-TI - Assistente interno de IA")
@RestController
@RequestMapping("/assistente")
@Validated
public class AssistenteInternoController {

    @Resource
    private AssistenteInternoService assistenteInternoService;

    @PostMapping("/perguntar")
    @Operation(summary = "Pergunta ao assistente (conversa nova ou continuação)")
    @PreAuthorize("@ss.hasPermission('assistente:usar')")
    @RateLimiter(time = 60, count = 20, keyResolver = UserRateLimiterKeyResolver.class)
    public CommonResult<AssistenteVOs.Resposta> perguntar(@Valid @RequestBody AssistenteVOs.PerguntaReq req) {
        return success(assistenteInternoService.perguntar(req, getLoginUserId()));
    }

    @GetMapping("/sessoes")
    @Operation(summary = "Minhas conversas")
    @PreAuthorize("@ss.hasPermission('assistente:usar')")
    public CommonResult<List<AssistenteVOs.Sessao>> sessoes() {
        return success(assistenteInternoService.sessoes(getLoginUserId()));
    }

    @GetMapping("/mensagens")
    @Operation(summary = "Mensagens de uma conversa minha")
    @PreAuthorize("@ss.hasPermission('assistente:usar')")
    public CommonResult<List<AssistenteVOs.Mensagem>> mensagens(@RequestParam("sessaoId") Long sessaoId) {
        return success(assistenteInternoService.mensagens(sessaoId, getLoginUserId()));
    }

    @DeleteMapping("/sessao")
    @Operation(summary = "Apagar uma conversa minha")
    @PreAuthorize("@ss.hasPermission('assistente:usar')")
    public CommonResult<Boolean> excluir(@RequestParam("sessaoId") Long sessaoId) {
        assistenteInternoService.excluir(sessaoId, getLoginUserId());
        return success(true);
    }

}
