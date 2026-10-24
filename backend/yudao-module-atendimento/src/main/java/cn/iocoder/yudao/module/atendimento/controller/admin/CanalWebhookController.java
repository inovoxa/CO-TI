package cn.iocoder.yudao.module.atendimento.controller.admin;

import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.atendimento.service.WebhookEntradaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhooks dos provedores, sem login do portal. A autenticação é do próprio provedor: assinatura
 * {@code X-Hub-Signature-256} com a chave secreta do app (Meta) ou o cabeçalho
 * {@code X-Telegram-Bot-Api-Secret-Token} cadastrado pelo CO-TI (Telegram). Resposta vazia e rápida: o conteúdo é
 * processado depois, numa fila.
 */
@Tag(name = "CO-TI - Atendimento: webhooks dos canais (Meta, Telegram)")
@RestController
@RequestMapping("/atendimento/canal")
public class CanalWebhookController {

    @Resource
    private WebhookEntradaService webhookEntradaService;

    @GetMapping(value = "/meta/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Verificação do webhook pela Meta (hub.challenge)")
    @PermitAll
    @RateLimiter(time = 60, count = 60, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<String> verificarMeta(@RequestParam(value = "hub.mode", required = false) String modo,
                                                @RequestParam(value = "hub.verify_token", required = false) String palavra,
                                                @RequestParam(value = "hub.challenge", required = false) String desafio) {
        String r = webhookEntradaService.verificarMeta(modo, palavra, desafio);
        return r == null ? ResponseEntity.status(403).body("") : ResponseEntity.ok(r);
    }

    @PostMapping(value = "/meta/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Eventos do WhatsApp Cloud API e do Instagram")
    @PermitAll
    @RateLimiter(time = 60, count = 6000, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<String> meta(@RequestHeader(value = "X-Hub-Signature-256", required = false) String assinatura,
                                       @RequestBody(required = false) byte[] corpo) {
        if (corpo == null || corpo.length == 0) {
            return ResponseEntity.badRequest().body("");
        }
        return webhookEntradaService.registrarMeta(corpo, assinatura) ? ResponseEntity.ok("") : ResponseEntity.status(401).body("");
    }

    @PostMapping(value = "/telegram/{caixaId}/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Atualizações do robô do Telegram")
    @PermitAll
    @RateLimiter(time = 60, count = 3000, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<String> telegram(@PathVariable("caixaId") Long caixaId,
                                           @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String segredo,
                                           @RequestBody(required = false) byte[] corpo) {
        if (corpo == null || corpo.length == 0) {
            return ResponseEntity.badRequest().body("");
        }
        return webhookEntradaService.registrarTelegram(caixaId, corpo, segredo) ? ResponseEntity.ok("") : ResponseEntity.status(401).body("");
    }

}
