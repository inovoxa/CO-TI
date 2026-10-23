package cn.iocoder.yudao.module.identidade.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.identidade.controller.admin.vo.MfaCodigoReqVO;
import cn.iocoder.yudao.module.identidade.service.MfaService;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
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

@Tag(name = "CO-TI - Verificação em duas etapas")
@RestController
@RequestMapping("/identidade/mfa")
@Validated
public class MfaController {

    @Resource
    private MfaService mfaService;
    @Resource
    private AdminUserService adminUserService;

    @GetMapping("/status")
    @Operation(summary = "Situação da verificação em duas etapas do usuário logado")
    public CommonResult<MfaService.Status> status() {
        return success(mfaService.status(getLoginUserId()));
    }

    @PostMapping("/iniciar")
    @Operation(summary = "Gera o segredo e o endereço do QR code (ainda não ativa)")
    public CommonResult<MfaService.Cadastro> iniciar() {
        return success(mfaService.iniciar(getLoginUserId(), loginAtual()));
    }

    @PostMapping("/confirmar")
    @Operation(summary = "Ativa com o primeiro código e devolve os códigos de recuperação")
    public CommonResult<List<String>> confirmar(@Valid @RequestBody MfaCodigoReqVO req) {
        return success(mfaService.confirmar(getLoginUserId(), loginAtual(), req.getCodigo()));
    }

    @PostMapping("/desativar")
    @Operation(summary = "Desativa (exige um código válido)")
    public CommonResult<Boolean> desativar(@Valid @RequestBody MfaCodigoReqVO req) {
        mfaService.desativar(getLoginUserId(), loginAtual(), req.getCodigo());
        return success(true);
    }

    @DeleteMapping("/usuario")
    @Operation(summary = "Remove a verificação em duas etapas de outro usuário (perdeu o celular)")
    @PreAuthorize("@ss.hasPermission('identidade:mfa:redefinir')")
    public CommonResult<Boolean> redefinir(@RequestParam("id") Long userId) {
        mfaService.redefinir(userId, getLoginUserId(), loginAtual());
        return success(true);
    }

    private String loginAtual() {
        AdminUserDO u = adminUserService.getUser(getLoginUserId());
        return u == null ? String.valueOf(getLoginUserId()) : u.getUsername();
    }

}
