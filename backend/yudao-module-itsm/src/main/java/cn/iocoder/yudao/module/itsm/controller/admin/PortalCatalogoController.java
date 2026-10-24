package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CatalogoVOs;
import cn.iocoder.yudao.module.itsm.service.AprovacaoService;
import cn.iocoder.yudao.module.itsm.service.FormularioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * Catálogo de serviços e "Minhas aprovações": qualquer usuário logado. O serviço confere quem é o aprovador.
 */
@Tag(name = "CO-TI - Catálogo de serviços e minhas aprovações")
@RestController
@RequestMapping("/itsm/portal")
@Validated
public class PortalCatalogoController {

    @Resource
    private FormularioService formularioService;
    @Resource
    private AprovacaoService aprovacaoService;

    @GetMapping("/catalogo")
    @Operation(summary = "Formulários ativos, agrupados pelo campo grupo")
    public CommonResult<List<CatalogoVOs.FormularioCatalogo>> catalogo() {
        return success(formularioService.catalogo());
    }

    @PostMapping("/catalogo/enviar")
    @Operation(summary = "Enviar um formulário: abre o chamado e devolve o número")
    public CommonResult<Long> enviar(@Valid @RequestBody CatalogoVOs.EnviarReq req) {
        return success(formularioService.enviar(req, getLoginUserId()));
    }

    @GetMapping("/aprovacoes")
    @Operation(summary = "Pedidos de aprovação endereçados a mim")
    public CommonResult<List<CatalogoVOs.AprovacaoResp>> minhas(@RequestParam(value = "pendentes", defaultValue = "true") boolean pendentes) {
        return success(aprovacaoService.converter(aprovacaoService.minhas(getLoginUserId(), pendentes), true));
    }

    @PostMapping("/aprovacoes/responder")
    @Operation(summary = "Aprovar ou recusar")
    public CommonResult<Boolean> responder(@Valid @RequestBody CatalogoVOs.ResponderAprovacaoReq req) {
        aprovacaoService.responder(req.getId(), req.getAprovar(), req.getComentario(), getLoginUserId());
        return success(true);
    }

}
