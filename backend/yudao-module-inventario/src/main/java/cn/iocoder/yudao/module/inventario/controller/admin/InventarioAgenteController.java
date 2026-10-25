package cn.iocoder.yudao.module.inventario.controller.admin;

import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.inventario.service.InventarioAgenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endereço dos agentes de inventário, sem login do portal. O nginx encaminha {@code /ocsinventory} (agente OCS) e
 * {@code /front/inventory.php} (GLPI Agent, como no GLPI) para cá. A proteção é a lista de redes permitidas e,
 * opcionalmente, usuário e senha HTTP Basic ({@code coti.inventario.*}).
 */
@Tag(name = "CO-TI - Inventário: agentes OCS e GLPI Agent")
@RestController
@RequestMapping("/inventario/agente")
public class InventarioAgenteController {

    @Resource
    private InventarioAgenteService inventarioAgenteService;

    @PostMapping
    @Operation(summary = "PROLOG/INVENTORY do agente OCS (XML zlib) ou contact/inventory do GLPI Agent (JSON)")
    @PermitAll
    @RateLimiter(time = 60, count = 600, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<byte[]> receber(HttpServletRequest request,
                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacao,
                                          @RequestBody(required = false) byte[] corpo) {
        InventarioAgenteService.Resposta r = inventarioAgenteService.receber(corpo, ServletUtils.getClientIP(request),
                request.getHeader(HttpHeaders.USER_AGENT), autorizacao);
        ResponseEntity.BodyBuilder b = ResponseEntity.status(r.status()).contentType(MediaType.parseMediaType(r.tipo()));
        r.cabecalhos().forEach(b::header);
        return b.body(r.corpo());
    }

    /** O agente OCS às vezes testa o endereço com GET antes de mandar. */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @PermitAll
    @RateLimiter(time = 60, count = 60, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("CO-TI");
    }

}
