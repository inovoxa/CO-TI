package cn.iocoder.yudao.module.identidade.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.identidade.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.identidade.controller.admin.vo.UsuarioGestaoVOs;
import cn.iocoder.yudao.module.identidade.service.UsuarioGestaoService;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** Sistema → Usuários do CO-TI: lista, ficha, ações em lote, revisão de acessos e planilha com prévia. */
@Tag(name = "CO-TI - Usuários")
@RestController
@RequestMapping("/identidade/usuarios")
@Validated
public class UsuarioGestaoController {

    /** Permissão de cada ação em lote (as mesmas dos botões de uma pessoa só). */
    private static final Map<String, String> PERMISSAO_LOTE = Map.of("ativar", "system:user:update", "desativar", "system:user:update",
            "mover", "system:user:update", "encerrar", "system:user:update", "perfil", "system:permission:assign-user-role",
            "tirar_perfil", "system:permission:assign-user-role", "mfa", "identidade:mfa:redefinir");

    @Resource
    private UsuarioGestaoService usuarioGestaoService;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @GetMapping("/page")
    @Operation(summary = "Usuários com perfis, origem, último acesso e verificação em duas etapas")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<PageResult<UsuarioGestaoVOs.Linha>> pagina(@Valid UsuarioGestaoVOs.PaginaReq req) {
        return success(usuarioGestaoService.pagina(req));
    }

    @GetMapping("/ficha")
    @Operation(summary = "Ficha: perfis, AD, segundo fator, sessões, acessos e auditoria")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<UsuarioGestaoVOs.Ficha> ficha(@RequestParam("id") Long id) {
        return success(usuarioGestaoService.ficha(id));
    }

    @PostMapping("/lote")
    @Operation(summary = "Ativar, desativar, mover, dar ou tirar perfil, redefinir segundo fator ou encerrar sessões de vários")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<UsuarioGestaoVOs.LoteResp> lote(@Valid @RequestBody UsuarioGestaoVOs.LoteReq req) {
        String permissao = PERMISSAO_LOTE.get(req.getAcao());
        if (permissao == null || !securityFrameworkService.hasPermission(permissao)) {
            throw new AccessDeniedException("sem permissão para " + req.getAcao());
        }
        return success(usuarioGestaoService.lote(req, getLoginUserId(), loginAtual()));
    }

    @GetMapping("/revisao")
    @Operation(summary = "Revisão de acessos: privilegiados, sem segundo fator, sem acesso, contas locais, desativados no AD")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<UsuarioGestaoVOs.Revisao> revisao() {
        return success(usuarioGestaoService.revisao());
    }

    @PostMapping("/revisado")
    @Operation(summary = "Registra na auditoria que a revisão de acessos foi feita")
    @PreAuthorize("@ss.hasPermission('system:user:update')")
    public CommonResult<Boolean> revisado(@RequestBody(required = false) Map<String, String> corpo) {
        usuarioGestaoService.revisado(getLoginUserId(), loginAtual(), corpo == null ? null : corpo.get("observacao"));
        return success(true);
    }

    @GetMapping("/exportar")
    @Operation(summary = "Usuários em planilha (sem senhas), com os filtros da tela")
    @PreAuthorize("@ss.hasPermission('system:user:export')")
    public void exportar(@Valid UsuarioGestaoVOs.PaginaReq req, HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "usuarios.xlsx", usuarioGestaoService.exportar(req, getLoginUserId()));
    }

    @PostMapping("/importar/analisar")
    @Operation(summary = "Prévia da planilha: quem muda e quem pode ser desativado, sem gravar nada")
    @PreAuthorize("@ss.hasPermission('system:user:import')")
    public CommonResult<ImportacaoVOs.Previa> analisar(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(usuarioGestaoService.analisar(entrada));
        }
    }

    @PostMapping("/importar")
    @Operation(summary = "Aplicar a planilha; só desativa os ausentes indicados em removerIds")
    @PreAuthorize("@ss.hasPermission('system:user:import')")
    public CommonResult<ImportacaoVOs.Resultado> importar(@RequestParam("arquivo") MultipartFile arquivo,
                                                         @RequestParam(value = "removerIds", required = false) List<Long> removerIds) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(usuarioGestaoService.importar(entrada, removerIds, getLoginUserId(), loginAtual()));
        }
    }

    private String loginAtual() {
        AdminUserDO u = adminUserService.getUser(getLoginUserId());
        return u == null ? String.valueOf(getLoginUserId()) : u.getUsername();
    }

}
