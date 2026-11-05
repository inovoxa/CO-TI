package cn.iocoder.yudao.module.infraestrutura.controller.admin;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.IntegracaoVOs;
import cn.iocoder.yudao.module.infraestrutura.service.IntegracaoApiService;
import cn.iocoder.yudao.module.infraestrutura.service.IntegracaoApiService.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

/** API para outros sistemas, sem login do portal: a autenticação é a chave, em {@code Authorization: Bearer coti_...}. */
@Tag(name = "CO-TI - API de integração")
@RestController
@RequestMapping("/integracao/v1/chamados")
@Validated
public class IntegracaoApiController {

    @Resource
    private IntegracaoApiService api;

    @PostMapping
    @Operation(summary = "Abre um chamado por formulário, com a validação e a aprovação normais")
    @PermitAll
    public ResponseEntity<Map<String, Object>> abrir(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencia,
                                                     @Valid @RequestBody IntegracaoVOs.AbrirChamado req) {
        return tratar(() -> {
            IntegracaoApiService.Criado c = api.abrir(api.autenticar(autorizacao, ServletUtils.getClientIP(), IntegracaoApiService.CRIAR), req, idempotencia);
            return ResponseEntity.status(c.repetido() ? 200 : 201).body(Map.of("id", c.id(), "repetido", c.repetido()));
        });
    }

    @GetMapping("/{id}")
    @Operation(summary = "Situação de um chamado aberto por esta chave")
    @PermitAll
    public ResponseEntity<?> consultar(@RequestHeader(value = "Authorization", required = false) String autorizacao, @PathVariable("id") Long id) {
        return tratar(() -> api.consultar(api.autenticar(autorizacao, ServletUtils.getClientIP(), IntegracaoApiService.CONSULTAR), id)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("erro", "não encontrado"))));
    }

    /** Sistema externo espera o código HTTP certo, não o envelope das telas. */
    @SuppressWarnings("unchecked")
    private static <T> ResponseEntity<T> tratar(Supplier<ResponseEntity<? extends Object>> f) {
        try {
            return (ResponseEntity<T>) f.get();
        } catch (ApiException e) {
            return (ResponseEntity<T>) switch (e.falha) {
                case NAO_AUTORIZADO -> ResponseEntity.status(401).body(Map.of("erro", "não autorizado"));
                case SEM_ESCOPO -> ResponseEntity.status(403).body(Map.of("erro", "esta chave não tem permissão para isso"));
                case LIMITE -> ResponseEntity.status(429).body(Map.of("erro", "limite de pedidos por minuto atingido"));
            };
        } catch (ServiceException e) {
            return (ResponseEntity<T>) ResponseEntity.unprocessableEntity().body(Map.of("erro", e.getMessage()));
        }
    }

}
