package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.service.gov.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Governança (fornecedores, contratos, licenças, orçamentos e projetos)")
@RestController
@RequestMapping("/itsm")
@Validated
public class GovernancaController {

    @Resource
    private FornecedorService fornecedorService;
    @Resource
    private ContratoService contratoService;
    @Resource
    private LicencaService licencaService;
    @Resource
    private OrcamentoService orcamentoService;
    @Resource
    private ProjetoService projetoService;
    @Resource
    private CustoService custoService;

    // ================= fornecedores =================

    @GetMapping("/fornecedor/page")
    @Operation(summary = "Fornecedores")
    @PreAuthorize("@ss.hasPermission('itsm:fornecedor:query')")
    public CommonResult<PageResult<GovernancaVOs.Fornecedor>> fornecedores(@Valid GovernancaVOs.Filtro f) {
        return success(fornecedorService.pagina(f));
    }

    @GetMapping("/fornecedor/simples")
    @Operation(summary = "Fornecedores ativos, para campos de escolha")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:fornecedor:query', 'itsm:contrato:query', 'itsm:licenca:query')")
    public CommonResult<List<GovernancaVOs.Resumo>> fornecedoresSimples() {
        return success(fornecedorService.simples());
    }

    @GetMapping("/fornecedor/get")
    @PreAuthorize("@ss.hasPermission('itsm:fornecedor:query')")
    public CommonResult<GovernancaVOs.Fornecedor> fornecedor(@RequestParam("id") Long id) {
        return success(fornecedorService.obter(id));
    }

    @PostMapping("/fornecedor/save")
    @PreAuthorize("@ss.hasPermission('itsm:fornecedor:update')")
    public CommonResult<Long> salvarFornecedor(@Valid @RequestBody GovernancaVOs.Fornecedor req) {
        return success(fornecedorService.salvar(req));
    }

    @DeleteMapping("/fornecedor/delete")
    @PreAuthorize("@ss.hasPermission('itsm:fornecedor:update')")
    public CommonResult<Boolean> excluirFornecedor(@RequestParam("id") Long id) {
        fornecedorService.excluir(id);
        return success(true);
    }

    // ================= contratos =================

    @GetMapping("/contrato/page")
    @Operation(summary = "Contratos, com a situação calculada (vigente, perto do prazo, vencido...)")
    @PreAuthorize("@ss.hasPermission('itsm:contrato:query')")
    public CommonResult<PageResult<GovernancaVOs.Contrato>> contratos(@Valid GovernancaVOs.Filtro f) {
        return success(contratoService.pagina(f));
    }

    @GetMapping("/contrato/simples")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:contrato:query', 'itsm:licenca:query')")
    public CommonResult<List<GovernancaVOs.Resumo>> contratosSimples() {
        return success(contratoService.simples());
    }

    @GetMapping("/contrato/get")
    @PreAuthorize("@ss.hasPermission('itsm:contrato:query')")
    public CommonResult<GovernancaVOs.Contrato> contrato(@RequestParam("id") Long id) {
        return success(contratoService.obter(id));
    }

    @PostMapping("/contrato/save")
    @PreAuthorize("@ss.hasPermission('itsm:contrato:update')")
    public CommonResult<Long> salvarContrato(@Valid @RequestBody GovernancaVOs.Contrato req) {
        return success(contratoService.salvar(req, getLoginUserId()));
    }

    @PostMapping("/contrato/renovar")
    @Operation(summary = "Registrar renovação expressa")
    @PreAuthorize("@ss.hasPermission('itsm:contrato:update')")
    public CommonResult<Boolean> renovar(@Valid @RequestBody GovernancaVOs.RenovarReq req) {
        contratoService.renovar(req);
        return success(true);
    }

    @DeleteMapping("/contrato/delete")
    @PreAuthorize("@ss.hasPermission('itsm:contrato:update')")
    public CommonResult<Boolean> excluirContrato(@RequestParam("id") Long id) {
        contratoService.excluir(id);
        return success(true);
    }

    @PostMapping("/governanca/verificar-alertas")
    @Operation(summary = "Rodar agora os avisos de contratos e licenças (a tarefa diária roda às 7h)")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:contrato:update', 'itsm:licenca:update')")
    public CommonResult<Map<String, Integer>> verificarAlertas(
            @RequestParam(value = "data", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        LocalDate hoje = data == null ? LocalDate.now() : data;
        return success(Map.of("contratos", contratoService.enviarAlertas(hoje), "licencas", licencaService.enviarAlertas(hoje)));
    }

    // ================= custos =================

    @GetMapping("/custo/list")
    @Operation(summary = "Custos de um contrato, projeto ou licença")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:contrato:query', 'itsm:projeto:query', 'itsm:licenca:query')")
    public CommonResult<List<GovernancaVOs.Custo>> custos(@RequestParam("objeto") String objeto, @RequestParam("objetoId") Long objetoId) {
        return success(custoService.listar(objeto, objetoId));
    }

    @PostMapping("/custo/save")
    @Operation(summary = "Lançar custo (a permissão depende do item: contrato, projeto ou licença)")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:contrato:update', 'itsm:projeto:update', 'itsm:licenca:update')")
    public CommonResult<Long> salvarCusto(@Valid @RequestBody GovernancaVOs.Custo req) {
        return success(custoService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/custo/delete")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:contrato:update', 'itsm:projeto:update', 'itsm:licenca:update')")
    public CommonResult<Boolean> excluirCusto(@RequestParam("id") Long id) {
        custoService.excluir(id, getLoginUserId());
        return success(true);
    }

    // ================= licenças =================

    @GetMapping("/licenca/page")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:query')")
    public CommonResult<PageResult<GovernancaVOs.Licenca>> licencas(@Valid GovernancaVOs.Filtro f) {
        return success(licencaService.pagina(f));
    }

    @GetMapping("/licenca/get")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:query')")
    public CommonResult<GovernancaVOs.Licenca> licenca(@RequestParam("id") Long id) {
        return success(licencaService.obter(id));
    }

    @GetMapping("/licenca/da-pessoa")
    @Operation(summary = "Licenças que uma pessoa usa")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:query')")
    public CommonResult<List<GovernancaVOs.Licenca>> licencasDaPessoa(@RequestParam("userId") Long userId) {
        return success(licencaService.daPessoa(userId));
    }

    @PostMapping("/licenca/save")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:update')")
    public CommonResult<Long> salvarLicenca(@Valid @RequestBody GovernancaVOs.Licenca req) {
        return success(licencaService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/licenca/delete")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:update')")
    public CommonResult<Boolean> excluirLicenca(@RequestParam("id") Long id) {
        licencaService.excluir(id);
        return success(true);
    }

    @PostMapping("/licenca/uso")
    @Operation(summary = "Entregar a licença a uma pessoa ou equipamento")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:update')")
    public CommonResult<Long> adicionarUso(@Valid @RequestBody GovernancaVOs.Uso req) {
        return success(licencaService.adicionarUso(req));
    }

    @DeleteMapping("/licenca/uso")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:update')")
    public CommonResult<Boolean> removerUso(@RequestParam("id") Long id) {
        licencaService.removerUso(id);
        return success(true);
    }

    @PostMapping("/licenca/revelar-chave")
    @Operation(summary = "Ver a chave (fica registrado na auditoria)")
    @PreAuthorize("@ss.hasPermission('itsm:licenca:ver-chave')")
    public CommonResult<String> revelarChave(@RequestParam("id") Long id) {
        return success(licencaService.revelarChave(id, getLoginUserId()));
    }

    // ================= orçamentos =================

    @GetMapping("/orcamento/page")
    @PreAuthorize("@ss.hasPermission('itsm:orcamento:query')")
    public CommonResult<PageResult<GovernancaVOs.Orcamento>> orcamentos(@Valid GovernancaVOs.Filtro f) {
        return success(orcamentoService.pagina(f));
    }

    @GetMapping("/orcamento/simples")
    @PreAuthorize("@ss.hasAnyPermissions('itsm:orcamento:query', 'itsm:contrato:update', 'itsm:projeto:update', 'itsm:licenca:update')")
    public CommonResult<List<GovernancaVOs.Resumo>> orcamentosSimples() {
        return success(orcamentoService.simples());
    }

    @GetMapping("/orcamento/get")
    @PreAuthorize("@ss.hasPermission('itsm:orcamento:query')")
    public CommonResult<GovernancaVOs.Orcamento> orcamento(@RequestParam("id") Long id) {
        return success(orcamentoService.obter(id));
    }

    @PostMapping("/orcamento/save")
    @PreAuthorize("@ss.hasPermission('itsm:orcamento:update')")
    public CommonResult<Long> salvarOrcamento(@Valid @RequestBody GovernancaVOs.Orcamento req) {
        return success(orcamentoService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/orcamento/delete")
    @PreAuthorize("@ss.hasPermission('itsm:orcamento:update')")
    public CommonResult<Boolean> excluirOrcamento(@RequestParam("id") Long id) {
        orcamentoService.excluir(id);
        return success(true);
    }

    // ================= projetos =================

    @GetMapping("/projeto/page")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:query')")
    public CommonResult<PageResult<GovernancaVOs.Projeto>> projetos(@Valid GovernancaVOs.Filtro f) {
        return success(projetoService.pagina(f, getLoginUserId()));
    }

    @GetMapping("/projeto/get")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:query')")
    public CommonResult<GovernancaVOs.Projeto> projeto(@RequestParam("id") Long id) {
        return success(projetoService.obter(id, getLoginUserId()));
    }

    @PostMapping("/projeto/save")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:update')")
    public CommonResult<Long> salvarProjeto(@Valid @RequestBody GovernancaVOs.Projeto req) {
        return success(projetoService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/projeto/delete")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:update')")
    public CommonResult<Boolean> excluirProjeto(@RequestParam("id") Long id) {
        projetoService.excluir(id);
        return success(true);
    }

    @PostMapping("/projeto/tarefa")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:update')")
    public CommonResult<Long> salvarTarefa(@Valid @RequestBody GovernancaVOs.Tarefa req) {
        return success(projetoService.salvarTarefa(req));
    }

    @DeleteMapping("/projeto/tarefa")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:update')")
    public CommonResult<Boolean> excluirTarefa(@RequestParam("id") Long id) {
        projetoService.excluirTarefa(id);
        return success(true);
    }

    @PostMapping("/projeto/tarefa/progresso")
    @Operation(summary = "Andamento da tarefa (o responsável por ela também pode)")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:query')")
    public CommonResult<Boolean> progresso(@Valid @RequestBody GovernancaVOs.Progresso req) {
        projetoService.progresso(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/projeto/vinculo")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:update')")
    public CommonResult<Boolean> vincular(@Valid @RequestBody GovernancaVOs.Vinculo req) {
        projetoService.vincular(req);
        return success(true);
    }

    @DeleteMapping("/projeto/vinculo")
    @PreAuthorize("@ss.hasPermission('itsm:projeto:update')")
    public CommonResult<Boolean> desvincular(@RequestParam("id") Long id) {
        projetoService.desvincular(id);
        return success(true);
    }

}
