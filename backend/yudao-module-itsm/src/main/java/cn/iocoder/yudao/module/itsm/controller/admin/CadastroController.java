package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CadastroVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.CategoriaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.OrigemDO;
import cn.iocoder.yudao.module.itsm.service.CadastroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CO-TI - Cadastros do Service Desk")
@RestController
@RequestMapping("/itsm")
@Validated
public class CadastroController {

    @Resource
    private CadastroService cadastroService;

    // -------- categorias --------

    @GetMapping("/categoria/list")
    @Operation(summary = "Categorias (árvore em lista, ordenada pelo nome completo)")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:categoria:query', 'itsm:chamado:query')")
    public CommonResult<List<CategoriaDO>> categorias(@RequestParam(value = "somenteAtivas", defaultValue = "false") boolean ativas) {
        return success(cadastroService.listarCategorias(ativas, false));
    }

    @PostMapping("/categoria/save")
    @Operation(summary = "Criar ou editar categoria")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:categoria:create', 'itsm:categoria:update')")
    public CommonResult<Long> salvarCategoria(@Valid @RequestBody CadastroVOs.CategoriaSalvar req) {
        return success(cadastroService.salvarCategoria(req));
    }

    @DeleteMapping("/categoria/delete")
    @Operation(summary = "Excluir categoria")
    @PreAuthorize("@ss.hasPermission('itsm:categoria:delete')")
    public CommonResult<Boolean> excluirCategoria(@RequestParam("id") Long id) {
        cadastroService.excluirCategoria(id);
        return success(true);
    }

    // -------- equipes --------

    @GetMapping("/equipe/list")
    @Operation(summary = "Equipes técnicas com membros")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:equipe:query', 'itsm:chamado:query')")
    public CommonResult<List<CadastroVOs.EquipeResp>> equipes(@RequestParam(value = "somenteAtivas", defaultValue = "false") boolean ativas) {
        return success(cadastroService.listarEquipes(ativas));
    }

    @PostMapping("/equipe/save")
    @Operation(summary = "Criar ou editar equipe (e definir os membros)")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:equipe:create', 'itsm:equipe:update')")
    public CommonResult<Long> salvarEquipe(@Valid @RequestBody CadastroVOs.EquipeSalvar req) {
        return success(cadastroService.salvarEquipe(req));
    }

    @DeleteMapping("/equipe/delete")
    @Operation(summary = "Excluir equipe")
    @PreAuthorize("@ss.hasPermission('itsm:equipe:delete')")
    public CommonResult<Boolean> excluirEquipe(@RequestParam("id") Long id) {
        cadastroService.excluirEquipe(id);
        return success(true);
    }

    // -------- origens --------

    @GetMapping("/origem/list")
    @Operation(summary = "Origens (canais de entrada)")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:origem:query', 'itsm:chamado:query')")
    public CommonResult<List<OrigemDO>> origens(@RequestParam(value = "somenteAtivas", defaultValue = "false") boolean ativas) {
        return success(cadastroService.listarOrigens(ativas));
    }

    @PostMapping("/origem/save")
    @Operation(summary = "Criar ou editar origem")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:origem:create', 'itsm:origem:update')")
    public CommonResult<Long> salvarOrigem(@Valid @RequestBody CadastroVOs.OrigemSalvar req) {
        return success(cadastroService.salvarOrigem(req));
    }

    @DeleteMapping("/origem/delete")
    @Operation(summary = "Excluir origem")
    @PreAuthorize("@ss.hasPermission('itsm:origem:delete')")
    public CommonResult<Boolean> excluirOrigem(@RequestParam("id") Long id) {
        cadastroService.excluirOrigem(id);
        return success(true);
    }

}
