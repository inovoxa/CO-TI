package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.TelefoneVOs;
import cn.iocoder.yudao.module.itsm.service.TelefoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CO-TI - Ramais e telefones")
@RestController
@RequestMapping("/itsm/telefone")
@Validated
public class TelefoneController {

    @Resource
    private TelefoneService telefoneService;

    @GetMapping("/page")
    @Operation(summary = "Busca na lista")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:query')")
    public CommonResult<PageResult<TelefoneVOs.Resp>> pagina(@Valid TelefoneVOs.PaginaReq req) {
        return success(telefoneService.pagina(req));
    }

    @GetMapping("/setores")
    @Operation(summary = "Setores existentes na lista (para o filtro)")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:query')")
    public CommonResult<List<String>> setores() {
        return success(telefoneService.setores());
    }

    @PostMapping("/salvar")
    @Operation(summary = "Criar ou alterar contato")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:update')")
    public CommonResult<Long> salvar(@Valid @RequestBody TelefoneVOs.SalvarReq req) {
        return success(telefoneService.salvar(req));
    }

    @DeleteMapping("/excluir")
    @Operation(summary = "Excluir contato")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:update')")
    public CommonResult<Boolean> excluir(@RequestParam("id") Long id) {
        telefoneService.excluir(id);
        return success(true);
    }

}
