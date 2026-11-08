package cn.iocoder.yudao.module.atendimento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.atendimento.service.WidgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Assistente da tela de login, para quem ainda não entrou. Sem login: a visita se identifica pelo token que recebeu
 * em {@code /iniciar} (cabeçalho {@code X-CO-TI-Visita}). Limite de requisições por endereço de origem.
 */
@Tag(name = "CO-TI - Atendimento: assistente da tela de login")
@RestController
@RequestMapping("/atendimento/canal/widget")
@Validated
public class CanalWidgetController {

    @Resource
    private WidgetService widgetService;

    @GetMapping("/disponivel")
    @Operation(summary = "Se o assistente está ligado (a tela de login só mostra o link nesse caso)")
    @PermitAll
    public CommonResult<Boolean> disponivel() {
        return success(widgetService.disponivel());
    }

    @PostMapping("/iniciar")
    @Operation(summary = "Começa uma visita e devolve o token dela")
    @PermitAll
    @RateLimiter(time = 60, count = 10, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<String> iniciar() {
        return success(widgetService.iniciar());
    }

    @PostMapping("/mensagem")
    @Operation(summary = "Mensagem do visitante")
    @PermitAll
    @RateLimiter(time = 60, count = 30, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<Boolean> mensagem(@RequestHeader(value = "X-CO-TI-Visita", required = false) String visita,
                                          @Valid @RequestBody Texto req) {
        widgetService.receber(visita, req.getTexto());
        return success(true);
    }

    @GetMapping("/mensagens")
    @Operation(summary = "Mensagens da visita depois de um id (a página consulta a cada poucos segundos)")
    @PermitAll
    @RateLimiter(time = 60, count = 120, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<List<WidgetService.Mensagem>> mensagens(@RequestHeader(value = "X-CO-TI-Visita", required = false) String visita,
                                                               @RequestParam(value = "depois", required = false) Long depois) {
        return success(widgetService.mensagens(visita, depois));
    }

    @Data
    public static class Texto {

        @NotBlank(message = "Escreva a mensagem")
        @Size(max = 1000, message = "Mensagem muito longa (até 1000 caracteres)")
        private String texto;

    }

}
