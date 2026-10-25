package cn.iocoder.yudao.module.monitoramento.controller.admin;

import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.monitoramento.service.ExecucaoMonitores;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Push: o serviço monitorado avisa que está vivo (cron, backup, script). Mesmo formato do Uptime Kuma:
 * {@code /api/push/<token>?status=up&msg=OK&ping=12} (o nginx encaminha para cá). Sem sinal no intervalo, o monitor cai.
 */
@Tag(name = "CO-TI - Monitoramento: push")
@RestController
@RequestMapping("/monitoramento/push")
public class PushController {

    @Resource
    private ExecucaoMonitores execucaoMonitores;

    @RequestMapping(value = "/{token}", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Sinal de vida de um monitor push (sem login; o token identifica o monitor)")
    @PermitAll
    @RateLimiter(time = 60, count = 600, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<Map<String, Object>> push(@PathVariable("token") String token,
                                                    @RequestParam(value = "status", required = false) String status,
                                                    @RequestParam(value = "msg", required = false) String msg,
                                                    @RequestParam(value = "ping", required = false) String ping) {
        if (!token.matches("[A-Za-z0-9_-]{8,64}")) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "msg", "Monitor não encontrado ou pausado."));
        }
        boolean ok = status == null || status.isBlank() || "up".equalsIgnoreCase(status.trim());
        Integer latencia = null;
        if (ping != null && ping.matches("\\d{1,7}(\\.\\d+)?")) {
            latencia = (int) Math.round(Double.parseDouble(ping));
        }
        String texto = msg == null ? null : msg.length() > 400 ? msg.substring(0, 400) : msg;
        if (!execucaoMonitores.registrarPush(token, ok, texto, latencia)) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "msg", "Monitor não encontrado ou pausado."));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

}
