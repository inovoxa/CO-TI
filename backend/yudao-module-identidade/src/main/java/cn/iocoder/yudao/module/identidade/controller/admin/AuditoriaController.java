package cn.iocoder.yudao.module.identidade.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.identidade.controller.admin.vo.AuditoriaPageReqVO;
import cn.iocoder.yudao.module.identidade.dal.dataobject.AuditoriaDO;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Trilha de auditoria")
@RestController
@RequestMapping("/identidade/auditoria")
@Validated
public class AuditoriaController {

    @Resource
    private AuditoriaService auditoriaService;

    @GetMapping("/page")
    @Operation(summary = "Consulta a trilha (mais recentes primeiro)")
    @PreAuthorize("@ss.hasPermission('identidade:auditoria:query')")
    public CommonResult<PageResult<AuditoriaDO>> page(@Valid AuditoriaPageReqVO req) {
        return success(auditoriaService.listar(req));
    }

    @GetMapping("/verificar")
    @Operation(summary = "Recalcula a cadeia de hashes e aponta a primeira linha adulterada")
    @PreAuthorize("@ss.hasPermission('identidade:auditoria:verificar')")
    public CommonResult<AuditoriaService.Verificacao> verificar() {
        AuditoriaService.Verificacao v = auditoriaService.verificar();
        // a própria verificação também fica registrada
        auditoriaService.registrar("auditoria.verificada", getLoginUserId(), null,
                Map.of("integra", v.isIntegra(), "conferidos", v.getRegistrosConferidos()));
        return success(v);
    }

}
