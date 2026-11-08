package cn.iocoder.yudao.module.infraestrutura.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.IntegracaoVOs;
import cn.iocoder.yudao.module.infraestrutura.service.IntegracaoApiService;
import cn.iocoder.yudao.module.infraestrutura.service.IntegracaoService;
import cn.iocoder.yudao.module.infraestrutura.service.WebhookSaidaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Integrações")
@RestController
@RequestMapping("/integracao")
@Validated
public class IntegracaoController {

    private static final int MAX_CORPO = 256 * 1024;
    private static final String VER = "@ss.hasPermission('infra:integracao:query')";
    private static final String ALTERAR = "@ss.hasPermission('infra:integracao:update')";

    @Resource
    private IntegracaoService integracaoService;
    @Resource
    private IntegracaoApiService integracaoApiService;
    @Resource
    private WebhookSaidaService webhookSaidaService;

    /**
     * Webhook, sem login do portal: a autenticação é o segredo da entrada. Assinatura: {@code X-COTI-Timestamp} (segundos desde 1970)
     * e {@code X-COTI-Signature}, o HMAC-SHA256 em hexadecimal de {@code timestamp.corpo}.
     */
    @PostMapping("/entrada/{codigo}")
    @Operation(summary = "Recebe um aviso de um sistema externo")
    @PermitAll
    @RateLimiter(time = 60, count = 600, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<Map<String, Object>> receber(@PathVariable("codigo") String codigo,
                                                       @RequestHeader(value = "Authorization", required = false) String autorizacao,
                                                       @RequestHeader(value = "X-COTI-Timestamp", required = false) String carimbo,
                                                       @RequestHeader(value = "X-COTI-Signature", required = false) String assinatura,
                                                       @RequestBody(required = false) byte[] corpo) {
        if (corpo == null || corpo.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("erro", "corpo vazio"));
        }
        if (corpo.length > MAX_CORPO) {
            return ResponseEntity.status(413).body(Map.of("erro", "corpo grande demais"));
        }
        IntegracaoService.Resultado r = integracaoService.receber(codigo, corpo, ServletUtils.getClientIP(), autorizacao, carimbo, assinatura);
        return switch (r.recepcao()) {
            case RECEBIDO -> ResponseEntity.accepted().body(Map.of("status", "recebido", "id", r.id()));
            case DUPLICADO -> ResponseEntity.ok(Map.of("status", "duplicado"));
            case INVALIDO -> ResponseEntity.badRequest().body(Map.of("erro", "o corpo precisa ser um objeto JSON"));
            case RECUSADO -> ResponseEntity.status(401).body(Map.of("erro", "não autorizado"));
        };
    }

    @GetMapping("/entradas")
    @Operation(summary = "Entradas cadastradas")
    @PreAuthorize(VER)
    public CommonResult<List<IntegracaoVOs.Entrada>> entradas() {
        return success(integracaoService.listar());
    }

    @PostMapping("/entradas")
    @Operation(summary = "Cria ou altera uma entrada; a nova nasce em simulação")
    @PreAuthorize(ALTERAR)
    public CommonResult<IntegracaoVOs.Salva> salvar(@Valid @RequestBody IntegracaoVOs.Entrada req) {
        return success(integracaoService.salvar(req, getLoginUserId()));
    }

    @PostMapping("/entradas/segredo")
    @Operation(summary = "Troca o segredo; o antigo deixa de valer na hora")
    @PreAuthorize(ALTERAR)
    public CommonResult<String> novoSegredo(@RequestParam("id") Long id) {
        return success(integracaoService.novoSegredo(id, getLoginUserId()));
    }

    @PostMapping("/entradas/testar")
    @Operation(summary = "Mostra o que um aviso faria, sem efeito nenhum")
    @PreAuthorize(ALTERAR)
    public CommonResult<IntegracaoVOs.Teste> testar(@Valid @RequestBody IntegracaoVOs.TesteReq req) {
        return success(integracaoService.testar(req.getId(), req.getJson()));
    }

    @GetMapping("/chaves")
    @Operation(summary = "Chaves de API")
    @PreAuthorize(VER)
    public CommonResult<List<IntegracaoVOs.ChaveApi>> chaves() {
        return success(integracaoApiService.listar());
    }

    @PostMapping("/chaves")
    @Operation(summary = "Cria uma chave; ela só aparece nesta resposta")
    @PreAuthorize(ALTERAR)
    public CommonResult<String> criarChave(@Valid @RequestBody IntegracaoVOs.ChaveApi req) {
        return success(integracaoApiService.criar(req, getLoginUserId()));
    }

    @PostMapping("/chaves/revogar")
    @Operation(summary = "Revoga uma chave; ela deixa de valer na hora")
    @PreAuthorize(ALTERAR)
    public CommonResult<Boolean> revogarChave(@RequestParam("id") Long id) {
        integracaoApiService.revogar(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/saidas")
    @Operation(summary = "Webhooks de saída")
    @PreAuthorize(VER)
    public CommonResult<List<IntegracaoVOs.Saida>> saidas() {
        return success(webhookSaidaService.listar());
    }

    @PostMapping("/saidas")
    @Operation(summary = "Cria ou altera um webhook de saída; o segredo só aparece na criação")
    @PreAuthorize(ALTERAR)
    public CommonResult<IntegracaoVOs.Salva> salvarSaida(@Valid @RequestBody IntegracaoVOs.Saida req) {
        return success(webhookSaidaService.salvar(req, getLoginUserId()));
    }

    @GetMapping("/entregas")
    @Operation(summary = "Histórico de entregas dos webhooks de saída")
    @PreAuthorize(VER)
    public CommonResult<PageResult<IntegracaoVOs.Entrega>> entregas(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                                    @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return success(webhookSaidaService.entregas(pageNo, Math.min(pageSize, 100)));
    }

    @GetMapping("/eventos")
    @Operation(summary = "Avisos recebidos, sem o conteúdo")
    @PreAuthorize(VER)
    public CommonResult<PageResult<IntegracaoVOs.Evento>> eventos(@Valid IntegracaoVOs.EventosReq req) {
        return success(integracaoService.eventos(req));
    }

}
