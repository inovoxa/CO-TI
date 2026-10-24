package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CatalogoVOs;
import cn.iocoder.yudao.module.itsm.dominio.MotorRegras;
import cn.iocoder.yudao.module.itsm.service.ExecucaoAdService;
import cn.iocoder.yudao.module.itsm.service.FormularioService;
import cn.iocoder.yudao.module.itsm.service.RegraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.FORMULARIO_INVALIDO;

@Tag(name = "CO-TI - Catálogo, regras e execuções no AD (configuração)")
@RestController
@RequestMapping("/itsm")
@Validated
public class CatalogoController {

    @Resource
    private FormularioService formularioService;
    @Resource
    private RegraService regraService;
    @Resource
    private ExecucaoAdService execucaoAdService;

    // -------- formulários --------

    @GetMapping("/formulario/list")
    @Operation(summary = "Formulários do catálogo (com a configuração)")
    @PreAuthorize("@ss.hasPermission('itsm:formulario:query')")
    public CommonResult<List<CatalogoVOs.FormularioResp>> formularios() {
        return success(formularioService.listar());
    }

    @PostMapping("/formulario/save")
    @Operation(summary = "Criar ou editar formulário")
    @PreAuthorize("@ss.hasPermission('itsm:formulario:update')")
    public CommonResult<Long> salvarFormulario(@Valid @RequestBody CatalogoVOs.FormularioSalvar req) {
        return success(formularioService.salvar(req));
    }

    @DeleteMapping("/formulario/delete")
    @Operation(summary = "Excluir formulário sem pedidos")
    @PreAuthorize("@ss.hasPermission('itsm:formulario:update')")
    public CommonResult<Boolean> excluirFormulario(@RequestParam("id") Long id) {
        formularioService.excluir(id);
        return success(true);
    }

    @PostMapping("/formulario/importar")
    @Operation(summary = "Importar formulários (formato formularios_definicoes.json); atualiza pelo código")
    @PreAuthorize("@ss.hasPermission('itsm:formulario:update')")
    public CommonResult<CatalogoVOs.ImportacaoResp> importar(@RequestParam("arquivo") MultipartFile arquivo,
                                                              @RequestParam(value = "criarCategorias", defaultValue = "true") boolean criarCategorias)
            throws IOException {
        if (arquivo.getSize() > 2 * 1024 * 1024) {
            throw exception(FORMULARIO_INVALIDO, "Arquivo grande demais (máximo 2 MB)");
        }
        return success(formularioService.importar(new String(arquivo.getBytes(), StandardCharsets.UTF_8), criarCategorias));
    }

    // -------- regras --------

    @GetMapping("/regra/list")
    @Operation(summary = "Regras de chamado, na ordem de avaliação")
    @PreAuthorize("@ss.hasPermission('itsm:regra:query')")
    public CommonResult<List<CatalogoVOs.RegraResp>> regras() {
        return success(regraService.listar());
    }

    @GetMapping("/regra/opcoes")
    @Operation(summary = "Campos, operadores e ações disponíveis")
    @PreAuthorize("@ss.hasPermission('itsm:regra:query')")
    public CommonResult<Map<String, Object>> opcoesRegra() {
        return success(Map.of("campos", MotorRegras.CAMPOS, "operadores", MotorRegras.OPERADORES, "acoes", MotorRegras.ACOES));
    }

    @PostMapping("/regra/save")
    @Operation(summary = "Criar ou editar regra")
    @PreAuthorize("@ss.hasPermission('itsm:regra:update')")
    public CommonResult<Long> salvarRegra(@Valid @RequestBody CatalogoVOs.RegraSalvar req) {
        return success(regraService.salvar(req));
    }

    @DeleteMapping("/regra/delete")
    @Operation(summary = "Excluir regra")
    @PreAuthorize("@ss.hasPermission('itsm:regra:update')")
    public CommonResult<Boolean> excluirRegra(@RequestParam("id") Long id) {
        regraService.excluir(id);
        return success(true);
    }

    @PostMapping("/regra/testar")
    @Operation(summary = "Mostra quais ações as regras ativas aplicariam a um chamado com esses dados")
    @PreAuthorize("@ss.hasPermission('itsm:regra:query')")
    public CommonResult<List<MotorRegras.Disparo>> testarRegras(@RequestBody CatalogoVOs.RegraTesteReq req) {
        return success(regraService.avaliar(req.getFatos()));
    }

    // -------- execuções no AD --------

    @GetMapping("/execucao/page")
    @Operation(summary = "Operações no AD pedidas pelos formulários")
    @PreAuthorize("@ss.hasPermission('itsm:execucao:query')")
    public CommonResult<PageResult<CatalogoVOs.ExecucaoResp>> execucoes(@Valid PageParam p,
                                                                         @RequestParam(value = "status", required = false) String status) {
        return success(execucaoAdService.pagina(p, status));
    }

    @GetMapping("/execucao/modo")
    @Operation(summary = "Modo da automação no AD: desligado, simulado ou ssh")
    @PreAuthorize("@ss.hasPermission('itsm:execucao:query')")
    public CommonResult<String> modo() {
        return success(execucaoAdService.modo());
    }

    @PostMapping("/execucao/repetir")
    @Operation(summary = "Repetir uma execução com erro ou cancelada")
    @PreAuthorize("@ss.hasPermission('itsm:execucao:update')")
    public CommonResult<Boolean> repetir(@RequestParam("id") Long id) {
        execucaoAdService.repetir(id);
        return success(true);
    }

    @PostMapping("/execucao/cancelar")
    @Operation(summary = "Cancelar uma execução (o técnico fará a operação manualmente)")
    @PreAuthorize("@ss.hasPermission('itsm:execucao:update')")
    public CommonResult<Boolean> cancelar(@Valid @RequestBody CatalogoVOs.CancelarExecucaoReq req) {
        execucaoAdService.cancelar(req.getId(), req.getMotivo());
        return success(true);
    }

}
