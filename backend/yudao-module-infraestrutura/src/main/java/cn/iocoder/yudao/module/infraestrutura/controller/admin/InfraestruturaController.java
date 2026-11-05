package cn.iocoder.yudao.module.infraestrutura.controller.admin;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.InfraVOs;
import cn.iocoder.yudao.module.infraestrutura.service.CapacidadeService;
import cn.iocoder.yudao.module.infraestrutura.service.RotinaRegistro;
import cn.iocoder.yudao.module.system.service.retencao.RetencaoService;
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

@Tag(name = "CO-TI - Infraestrutura")
@RestController
@RequestMapping("/infraestrutura")
@Validated
public class InfraestruturaController {

    private static final ErrorCode RETENCAO_INVALIDA = new ErrorCode(1_098_000_000, "{}");

    @Resource
    private CapacidadeService capacidadeService;
    @Resource
    private RetencaoService retencaoService;
    @Resource
    private RotinaRegistro rotinaRegistro;
    @Resource
    private AuditoriaService auditoria;

    @GetMapping("/capacidade")
    @Operation(summary = "Espaço, crescimento e previsão")
    @PreAuthorize("@ss.hasPermission('infra:capacidade:query')")
    public CommonResult<InfraVOs.Capacidade> capacidade() {
        return success(capacidadeService.obter());
    }

    @GetMapping("/retencao")
    @Operation(summary = "Por quanto tempo cada dado fica guardado")
    @PreAuthorize("@ss.hasPermission('infra:capacidade:query')")
    public CommonResult<List<RetencaoService.Item>> retencao() {
        return success(retencaoService.listar());
    }

    @PutMapping("/retencao")
    @Operation(summary = "Altera um prazo de retenção ou limite")
    @PreAuthorize("@ss.hasPermission('infra:retencao:update')")
    public CommonResult<Boolean> salvarRetencao(@Valid @RequestBody InfraVOs.SalvarRetencao req) {
        Long userId = getLoginUserId();
        int anterior;
        try {
            anterior = retencaoService.salvar(req.getChave(), req.getValor(), String.valueOf(userId));
        } catch (IllegalArgumentException e) {
            throw exception(RETENCAO_INVALIDA, e.getMessage());
        }
        auditoria.registrar("infra.retencao_alterada", userId, null, Map.of("item", req.getChave(), "antes", anterior, "depois", req.getValor()));
        return success(true);
    }

    @GetMapping("/rotinas")
    @Operation(summary = "Última execução de cada rotina agendada")
    @PreAuthorize("@ss.hasPermission('infra:rotina:query')")
    public CommonResult<List<InfraVOs.Rotina>> rotinas() {
        return success(rotinaRegistro.listar());
    }

}
