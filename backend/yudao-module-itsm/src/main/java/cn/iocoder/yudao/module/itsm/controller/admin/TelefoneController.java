package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.TelefoneVOs;
import cn.iocoder.yudao.module.itsm.service.TelefoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

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
        return success(telefoneService.pagina(req, getLoginUserId()));
    }

    @GetMapping("/setores")
    @Operation(summary = "Setores existentes na lista (para o filtro)")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:query')")
    public CommonResult<List<String>> setores() {
        return success(telefoneService.setores());
    }

    @GetMapping("/unidades")
    @Operation(summary = "Unidades, com o estado do link (para o filtro e o cadastro)")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:query')")
    public CommonResult<List<TelefoneVOs.Unidade>> unidades() {
        return success(telefoneService.unidades());
    }

    @GetMapping("/da-unidade")
    @Operation(summary = "Contatos de uma unidade, indicada pelo id ou por um setor dela")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:query')")
    public CommonResult<TelefoneVOs.ContatosUnidade> daUnidade(@RequestParam(value = "unidadeId", required = false) Long unidadeId,
                                                               @RequestParam(value = "deptId", required = false) Long deptId) {
        return success(telefoneService.daUnidade(unidadeId, deptId, getLoginUserId()));
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

    @GetMapping("/modelo")
    @Operation(summary = "Planilha modelo para importar contatos")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:update')")
    public void modelo(HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "modelo-contatos.xlsx", telefoneService.modelo());
    }

    @PostMapping("/importar")
    @Operation(summary = "Importar contatos de uma planilha .xlsx")
    @PreAuthorize("@ss.hasPermission('itsm:telefone:update')")
    public CommonResult<TelefoneVOs.ImportacaoResp> importar(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(telefoneService.importar(entrada));
        }
    }

}
