package cn.iocoder.yudao.module.inventario.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.UnidadeRedeVOs;
import cn.iocoder.yudao.module.inventario.service.UnidadeRedeService;
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

/** Unidade pela faixa de IP (Inventário → Rede). */
@Tag(name = "CO-TI - Inventário: unidade pela faixa de IP")
@RestController
@RequestMapping("/inventario/unidade-rede")
@Validated
public class UnidadeRedeController {

    @Resource
    private UnidadeRedeService unidadeRedeService;

    @GetMapping("/list")
    @Operation(summary = "Faixas cadastradas, com quantas máquinas caem em cada uma")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<List<UnidadeRedeVOs.Faixa>> listar() {
        return success(unidadeRedeService.listar());
    }

    @GetMapping("/sugestoes")
    @Operation(summary = "Redes /24 sem faixa em que a maioria das máquinas já é de uma unidade")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<List<UnidadeRedeVOs.Sugestao>> sugestoes() {
        return success(unidadeRedeService.sugestoes());
    }

    @PostMapping("/save")
    @Operation(summary = "Cadastrar ou alterar uma faixa")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Long> salvar(@Valid @RequestBody UnidadeRedeVOs.SalvarReq req) {
        return success(unidadeRedeService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Excluir uma faixa")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Boolean> excluir(@RequestParam("id") Long id) {
        unidadeRedeService.excluir(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/aplicar")
    @Operation(summary = "Passar os ativos para a unidade da faixa do IP (menos os de unidade escolhida à mão)")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<Integer> aplicar() {
        return success(unidadeRedeService.aplicar(getLoginUserId()));
    }

    @GetMapping("/exportar")
    @Operation(summary = "Faixas em planilha")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public void exportar(HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "faixas-ip.xlsx", unidadeRedeService.exportar(getLoginUserId()));
    }

    @PostMapping("/importar/analisar")
    @Operation(summary = "Prévia da planilha: o que será adicionado, atualizado e removido, sem gravar nada")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<ImportacaoVOs.Previa> analisar(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(unidadeRedeService.analisar(entrada));
        }
    }

    @PostMapping("/importar")
    @Operation(summary = "Aplicar a planilha; só remove as faixas ausentes indicadas em removerIds")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:update')")
    public CommonResult<ImportacaoVOs.Resultado> importar(@RequestParam("arquivo") MultipartFile arquivo,
                                                         @RequestParam(value = "removerIds", required = false) List<Long> removerIds) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return success(unidadeRedeService.importar(entrada, removerIds, getLoginUserId()));
        }
    }

}
