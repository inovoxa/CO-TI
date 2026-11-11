package cn.iocoder.yudao.module.infraestrutura.controller.admin;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.identidade.service.AdConfiguracaoService;
import cn.iocoder.yudao.module.identidade.service.AdSincronizacaoService;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.InfraVOs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** Tela "Diretório LDAP (AD)": a mesma configuração do GLPI, sem variáveis de ambiente nem reinício. */
@Tag(name = "CO-TI - Infraestrutura - Diretório AD")
@RestController
@RequestMapping("/infraestrutura/ad")
@Validated
public class DiretorioAdController {

    private static final ErrorCode AD_INVALIDO = new ErrorCode(1_098_000_010, "{}");

    @Resource
    private AdConfiguracaoService configuracao;
    @Resource
    private AdSincronizacaoService sincronizacao;
    @Resource
    private AuditoriaService auditoria;

    @GetMapping
    @Operation(summary = "Configuração em vigor (sem a senha)")
    @PreAuthorize("@ss.hasPermission('infra:ad:query')")
    public CommonResult<AdConfiguracaoService.Tela> obter() {
        return success(configuracao.tela());
    }

    @PutMapping
    @Operation(summary = "Salva e aplica na hora")
    @PreAuthorize("@ss.hasPermission('infra:ad:update')")
    public CommonResult<Boolean> salvar(@Valid @RequestBody InfraVOs.SalvarAd req) {
        Long userId = getLoginUserId();
        List<String> mudou;
        try {
            mudou = configuracao.salvar(req.getDados(), req.getSenha(), String.valueOf(userId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw exception(AD_INVALIDO, e.getMessage());
        }
        auditoria.registrar("infra.ad_alterado", userId, null, Map.of("campos", mudou));
        return success(true);
    }

    @PostMapping("/testar")
    @Operation(summary = "Testa a configuração da tela sem salvar")
    @PreAuthorize("@ss.hasPermission('infra:ad:update')")
    public CommonResult<AdConfiguracaoService.Teste> testar(@Valid @RequestBody InfraVOs.TestarAd req) {
        return success(configuracao.testar(req.getDados(), req.getSenha(), req.getLogin(), req.getSenhaUsuario()));
    }

    @PostMapping("/certificados")
    @Operation(summary = "Certificados que o servidor apresenta, para conferir e confiar")
    @PreAuthorize("@ss.hasPermission('infra:ad:update')")
    public CommonResult<List<AdConfiguracaoService.CertificadoInfo>> certificados(@Valid @RequestBody InfraVOs.CertificadosAd req) {
        try {
            return success(configuracao.certificados(req.getServidores(), req.getPorta(), req.getSeguranca()));
        } catch (IllegalArgumentException e) {
            throw exception(AD_INVALIDO, e.getMessage());
        }
    }

    @PostMapping("/sincronizar")
    @Operation(summary = "Roda a sincronização completa agora")
    @PreAuthorize("@ss.hasPermission('infra:ad:update')")
    public CommonResult<AdSincronizacaoService.Resumo> sincronizar() {
        try {
            return success(sincronizacao.sincronizarTudo());
        } catch (RuntimeException e) {
            throw exception(AD_INVALIDO, AdConfiguracaoService.explicar(e));
        }
    }

}
