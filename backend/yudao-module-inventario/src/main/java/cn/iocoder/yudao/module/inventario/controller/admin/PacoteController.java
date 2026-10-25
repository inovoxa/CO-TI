package cn.iocoder.yudao.module.inventario.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.PacoteVOs;
import cn.iocoder.yudao.module.inventario.dominio.Redes;
import cn.iocoder.yudao.module.inventario.service.PacoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Inventário: pacotes (teledeploy)")
@RestController
@RequestMapping("/inventario")
@Validated
public class PacoteController {

    @Resource
    private PacoteService pacoteService;
    @Resource
    private InventarioProperties inventarioProperties;

    @GetMapping("/pacote/list")
    @Operation(summary = "Pacotes, com quantas máquinas em cada situação")
    @PreAuthorize("@ss.hasPermission('inventario:pacote:query')")
    public CommonResult<List<PacoteVOs.Pacote>> listar() {
        return success(pacoteService.listar());
    }

    @GetMapping("/pacote/get")
    @PreAuthorize("@ss.hasPermission('inventario:pacote:query')")
    public CommonResult<PacoteVOs.Pacote> obter(@RequestParam("id") Long id) {
        return success(pacoteService.obter(id));
    }

    @PostMapping("/pacote/save")
    @Operation(summary = "Criar ou alterar pacote (o que executa não muda depois de implantado)")
    @PreAuthorize("@ss.hasPermission('inventario:pacote:update')")
    public CommonResult<Long> salvar(@Valid @RequestBody PacoteVOs.Pacote req) {
        return success(pacoteService.salvar(req, getLoginUserId()));
    }

    /** Arquivo em fluxo (corpo bruto), sem o limite do multipart: pacotes passam de 16 MB. */
    @PutMapping(value = "/pacote/arquivo", consumes = MediaType.ALL_VALUE)
    @Operation(summary = "Enviar o arquivo do pacote (corpo bruto: zip para Windows, tar.gz para Linux)")
    @PreAuthorize("@ss.hasPermission('inventario:pacote:update')")
    public CommonResult<PacoteVOs.Pacote> arquivo(@RequestParam("id") Long id, @RequestParam("nome") String nome,
                                                  HttpServletRequest request) throws IOException {
        return success(pacoteService.receberArquivo(id, nome, request.getInputStream(), getLoginUserId()));
    }

    @PostMapping("/pacote/implantar")
    @Operation(summary = "Pedir o pacote para máquinas (ou uma unidade) com agente OCS")
    @PreAuthorize("@ss.hasPermission('inventario:pacote:update')")
    public CommonResult<PacoteVOs.Resultado> implantar(@Valid @RequestBody PacoteVOs.ImplantarReq req) {
        return success(pacoteService.implantar(req, getLoginUserId()));
    }

    @PostMapping("/pacote/cancelar")
    @PreAuthorize("@ss.hasPermission('inventario:pacote:update')")
    public CommonResult<Boolean> cancelar(@RequestParam("id") Long implantacaoId) {
        pacoteService.cancelar(implantacaoId, getLoginUserId());
        return success(true);
    }

    @GetMapping("/pacote/implantacoes")
    @Operation(summary = "Situação por máquina")
    @PreAuthorize("@ss.hasPermission('inventario:pacote:query')")
    public CommonResult<PageResult<PacoteVOs.Implantacao>> implantacoes(@Valid PacoteVOs.ImplantacoesReq req) {
        return success(pacoteService.implantacoes(req));
    }

    // ---------------------------------------------------------------- download pelos agentes (sem login)

    @GetMapping(value = "/download/{fileid}/info", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Arquivo info do pacote (agente OCS)")
    @PermitAll
    @RateLimiter(time = 60, count = 1200, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<byte[]> info(@PathVariable("fileid") String fileid, HttpServletRequest request) {
        if (!Redes.permitido(ServletUtils.getClientIP(request), inventarioProperties.getAgenteRedes())) {
            return ResponseEntity.status(403).build();
        }
        byte[] info = fileid.matches("\\d{1,20}") ? pacoteService.info(fileid) : null;
        return info == null ? ResponseEntity.notFound().build() : ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(info);
    }

    @GetMapping("/download/{fileid}/{fragmento}")
    @Operation(summary = "Fragmento do pacote (agente OCS)")
    @PermitAll
    @RateLimiter(time = 60, count = 6000, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public ResponseEntity<FileSystemResource> fragmento(@PathVariable("fileid") String fileid, @PathVariable("fragmento") String fragmento,
                                                        HttpServletRequest request) {
        if (!Redes.permitido(ServletUtils.getClientIP(request), inventarioProperties.getAgenteRedes())) {
            return ResponseEntity.status(403).build();
        }
        // <fileid>-<n>, só dígitos (nada de caminho)
        if (!fileid.matches("\\d{1,20}") || !fragmento.matches(fileid + "-\\d{1,6}")) {
            return ResponseEntity.notFound().build();
        }
        Path f = pacoteService.fragmento(fileid, Integer.parseInt(fragmento.substring(fileid.length() + 1)));
        return f == null ? ResponseEntity.notFound().build()
                : ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new FileSystemResource(f));
    }

}
