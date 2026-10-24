package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ItilHistoricoDO;
import cn.iocoder.yudao.module.itsm.service.MudancaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Mudanças")
@RestController
@RequestMapping("/itsm/mudanca")
@Validated
public class MudancaController {

    private static final String Q = "@ss.hasPermission('itsm:mudanca:query')";
    private static final String U = "@ss.hasPermission('itsm:mudanca:update')";

    @Resource
    private MudancaService mudancaService;

    @GetMapping("/page")
    @Operation(summary = "Fila")
    @PreAuthorize(Q)
    public CommonResult<PageResult<ItilVOs.Resp>> pagina(@Valid ItilVOs.PaginaReq req) {
        return success(mudancaService.pagina(req, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "Detalhe, com as ações permitidas ao usuário")
    @PreAuthorize(Q)
    public CommonResult<ItilVOs.Resp> obter(@RequestParam("id") Long id) {
        return success(mudancaService.obter(id, getLoginUserId()));
    }

    @GetMapping("/linha-do-tempo")
    @Operation(summary = "Acompanhamentos, tarefas e soluções")
    @PreAuthorize(Q)
    public CommonResult<List<ChamadoVOs.Interacao>> linhaDoTempo(@RequestParam("id") Long id) {
        return success(mudancaService.linhaDoTempo(id));
    }

    @GetMapping("/historico")
    @Operation(summary = "Histórico de alterações")
    @PreAuthorize(Q)
    public CommonResult<List<ItilHistoricoDO>> historico(@RequestParam("id") Long id) {
        return success(mudancaService.historico(id));
    }

    @GetMapping("/vinculos")
    @Operation(summary = "Chamados, problemas e mudanças ligados")
    @PreAuthorize(Q)
    public CommonResult<List<ItilVOs.Vinculo>> vinculos(@RequestParam("id") Long id) {
        return success(mudancaService.vinculos(id));
    }

    @PostMapping("/create")
    @Operation(summary = "Registrar")
    @PreAuthorize(U)
    public CommonResult<Long> criar(@Valid @RequestBody ItilVOs.SalvarReq req) {
        return success(mudancaService.criar(req, getLoginUserId()));
    }

    @PostMapping("/do-chamado")
    @Operation(summary = "Registrar a partir de um chamado (copia os dados e vincula)")
    @PreAuthorize(U + " and @ss.hasPermission('itsm:chamado:query')")
    public CommonResult<Long> criarDoChamado(@RequestParam("chamadoId") Long chamadoId) {
        return success(mudancaService.criarDoChamado(chamadoId, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "Editar")
    @PreAuthorize(U)
    public CommonResult<Boolean> atualizar(@Valid @RequestBody ItilVOs.SalvarReq req) {
        mudancaService.atualizar(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Excluir")
    @PreAuthorize("@ss.hasPermission('itsm:mudanca:delete')")
    public CommonResult<Boolean> excluir(@RequestParam("id") Long id) {
        mudancaService.excluir(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/ator")
    @Operation(summary = "Incluir participante")
    @PreAuthorize(U)
    public CommonResult<Boolean> adicionarAtor(@Valid @RequestBody ItilVOs.AtorReq req) {
        mudancaService.adicionarAtor(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/ator")
    @Operation(summary = "Remover participante")
    @PreAuthorize(U)
    public CommonResult<Boolean> removerAtor(@RequestParam("id") Long atorId) {
        mudancaService.removerAtor(atorId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/assumir")
    @Operation(summary = "Entrar como técnico")
    @PreAuthorize(U)
    public CommonResult<Boolean> assumir(@RequestParam("id") Long id) {
        mudancaService.assumir(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/interacao")
    @Operation(summary = "Acompanhamento, tarefa ou solução")
    @PreAuthorize(U)
    public CommonResult<Long> interacao(@Valid @RequestBody ItilVOs.InteracaoReq req) {
        return success(mudancaService.adicionarInteracao(req, getLoginUserId()));
    }

    @PostMapping("/status")
    @Operation(summary = "Mudar a situação")
    @PreAuthorize(U)
    public CommonResult<Boolean> status(@Valid @RequestBody ItilVOs.StatusReq req) {
        mudancaService.mudarStatus(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/fechar")
    @Operation(summary = "Fechar")
    @PreAuthorize(U)
    public CommonResult<Boolean> fechar(@RequestParam("id") Long id) {
        mudancaService.fechar(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/reabrir")
    @Operation(summary = "Reabrir")
    @PreAuthorize(U)
    public CommonResult<Boolean> reabrir(@RequestParam("id") Long id) {
        mudancaService.reabrir(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/vinculo")
    @Operation(summary = "Vincular")
    @PreAuthorize(U)
    public CommonResult<Boolean> vincular(@Valid @RequestBody ItilVOs.VinculoReq req) {
        mudancaService.vincular(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/vinculo")
    @Operation(summary = "Desfazer vínculo")
    @PreAuthorize(U)
    public CommonResult<Boolean> desvincular(@RequestParam("id") Long vinculoId) {
        mudancaService.desvincular(vinculoId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/cancelar")
    @Operation(summary = "Cancelar (antes de aplicar)")
    @PreAuthorize(U)
    public CommonResult<Boolean> cancelar(@RequestParam("id") Long id, @RequestParam(value = "motivo", required = false) String motivo) {
        mudancaService.cancelar(id, motivo, getLoginUserId());
        return success(true);
    }

    @PostMapping("/pedir-aprovacao")
    @Operation(summary = "Pedir aprovação (todos precisam aprovar)")
    @PreAuthorize(U)
    public CommonResult<Boolean> pedirAprovacao(@Valid @RequestBody ItilVOs.PedirAprovacaoReq req) {
        mudancaService.pedirAprovacao(req, getLoginUserId());
        return success(true);
    }

    @GetMapping("/aprovacoes")
    @Operation(summary = "Aprovações da mudança")
    @PreAuthorize(Q)
    public CommonResult<List<ItilVOs.Aprovacao>> aprovacoes(@RequestParam("id") Long id) {
        return success(mudancaService.aprovacoes(id));
    }

    // o aprovador não precisa ver mudanças no menu: o serviço confere que a aprovação é dele

    @GetMapping("/minhas-aprovacoes")
    @Operation(summary = "Aprovações de mudança pedidas a mim")
    public CommonResult<List<ItilVOs.Aprovacao>> minhas(@RequestParam(value = "pendentes", defaultValue = "true") boolean pendentes) {
        return success(mudancaService.minhas(getLoginUserId(), pendentes));
    }

    @PostMapping("/responder-aprovacao")
    @Operation(summary = "Aprovar ou recusar")
    public CommonResult<Boolean> responder(@Valid @RequestBody ItilVOs.ResponderAprovacaoReq req) {
        mudancaService.responderAprovacao(req, getLoginUserId());
        return success(true);
    }

}
