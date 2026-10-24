package cn.iocoder.yudao.module.atendimento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.service.EntradaApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Entrada do canal API para sistemas externos. Sem login do portal: a autenticação é o token da caixa no cabeçalho
 * {@code X-CO-TI-Token} (guardado só como SHA-256). Limite de requisições por endereço de origem.
 */
@Tag(name = "CO-TI - Atendimento: canal API (sistemas externos)")
@RestController
@RequestMapping("/atendimento/canal/api")
@Validated
public class CanalApiController {

    @Resource
    private EntradaApiService entradaApiService;

    @PostMapping("/mensagem")
    @Operation(summary = "Mensagem do contato para a central")
    @PermitAll
    @RateLimiter(time = 60, count = 600, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<AtendimentoVOs.EntradaApiResp> mensagem(@RequestHeader(value = "X-CO-TI-Token", required = false) String token,
                                                               @Valid @RequestBody AtendimentoVOs.EntradaApi req) {
        return success(entradaApiService.receber(token, req));
    }

    @PostMapping("/status")
    @Operation(summary = "Aviso de entrega ou leitura de uma resposta")
    @PermitAll
    @RateLimiter(time = 60, count = 600, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<Boolean> status(@RequestHeader(value = "X-CO-TI-Token", required = false) String token,
                                        @Valid @RequestBody AtendimentoVOs.StatusApi req) {
        return success(entradaApiService.status(token, req));
    }

}
