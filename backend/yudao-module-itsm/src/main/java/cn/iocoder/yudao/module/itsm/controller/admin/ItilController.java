package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.service.ChamadoEscopo;
import cn.iocoder.yudao.module.itsm.service.ItilApoio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.CHAMADO_NAO_EXISTE;

@Tag(name = "CO-TI - Problemas e mudanças de um chamado")
@RestController
@RequestMapping("/itsm/itil")
@Validated
public class ItilController {

    @Resource
    private ItilApoio apoio;
    @Resource
    private ChamadoEscopo escopo;

    @GetMapping("/do-chamado")
    @Operation(summary = "Problemas e mudanças ligados ao chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query') and @ss.hasAnyPermissions('itsm:problema:query', 'itsm:mudanca:query')")
    public CommonResult<List<ItilVOs.Vinculo>> doChamado(@RequestParam("chamadoId") Long chamadoId) {
        if (!escopo.visivel(chamadoId)) {
            throw exception(CHAMADO_NAO_EXISTE);
        }
        return success(apoio.doChamado(chamadoId));
    }

}
