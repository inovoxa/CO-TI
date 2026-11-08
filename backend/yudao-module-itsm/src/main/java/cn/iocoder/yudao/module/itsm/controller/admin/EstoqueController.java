package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.EstoqueVOs;
import cn.iocoder.yudao.module.itsm.service.EstoqueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Estoque GTI")
@RestController
@RequestMapping("/itsm/estoque")
@Validated
public class EstoqueController {

    @Resource
    private EstoqueService estoqueService;

    @GetMapping("/tipos")
    @Operation(summary = "Tipos de item")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:query')")
    public CommonResult<List<EstoqueVOs.Tipo>> tipos() {
        return success(estoqueService.tipos());
    }

    @PostMapping("/tipo/salvar")
    @Operation(summary = "Criar ou alterar um tipo; o controle não muda depois de criado")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:update')")
    public CommonResult<Long> salvarTipo(@Valid @RequestBody EstoqueVOs.Tipo req) {
        return success(estoqueService.salvarTipo(req));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Saldo por tipo")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:query')")
    public CommonResult<List<EstoqueVOs.Resumo>> resumo() {
        return success(estoqueService.resumo());
    }

    @GetMapping("/page")
    @Operation(summary = "Busca de itens")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:query')")
    public CommonResult<PageResult<EstoqueVOs.ItemResp>> pagina(@Valid EstoqueVOs.PaginaReq req) {
        return success(estoqueService.pagina(req));
    }

    @PostMapping("/salvar")
    @Operation(summary = "Cadastrar ou corrigir um item; situação e saldo só mudam por movimento")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:update')")
    public CommonResult<Long> salvar(@Valid @RequestBody EstoqueVOs.ItemSalvar req) {
        return success(estoqueService.salvar(req, getLoginUserId()));
    }

    @PostMapping("/movimentar")
    @Operation(summary = "Registrar entrada, retirada, devolução, manutenção, retorno ou baixa")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:update')")
    public CommonResult<Boolean> movimentar(@Valid @RequestBody EstoqueVOs.MovimentarReq req) {
        estoqueService.movimentar(req, getLoginUserId());
        return success(true);
    }

    @GetMapping("/do-chamado")
    @Operation(summary = "Movimentos ligados a um chamado")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:query')")
    public CommonResult<List<EstoqueVOs.MovimentoResp>> doChamado(@RequestParam("chamadoId") Long chamadoId) {
        return success(estoqueService.doChamado(chamadoId));
    }

    @GetMapping("/relatorio")
    @Operation(summary = "Tudo o que o filtro alcança, para imprimir ou salvar em PDF")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:query')")
    public CommonResult<List<EstoqueVOs.ItemResp>> relatorio(@Valid EstoqueVOs.PaginaReq req) {
        return success(estoqueService.relatorio(req));
    }

    @GetMapping("/exportar")
    @Operation(summary = "Planilha com tudo o que o filtro alcança")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:query')")
    public void exportar(@Valid EstoqueVOs.PaginaReq req, HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "estoque-gti.xlsx", estoqueService.planilha(req));
    }

    @GetMapping("/historico")
    @Operation(summary = "Por quem o item passou")
    @PreAuthorize("@ss.hasPermission('itsm:estoque:query')")
    public CommonResult<List<EstoqueVOs.MovimentoResp>> historico(@RequestParam("itemId") Long itemId) {
        return success(estoqueService.historico(itemId));
    }

}
