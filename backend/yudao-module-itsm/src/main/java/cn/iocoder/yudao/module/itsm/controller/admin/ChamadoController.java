package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.HistoricoDO;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Chamados (equipe técnica)")
@RestController
@RequestMapping("/itsm/chamado")
@Validated
public class ChamadoController {

    @Resource
    private ChamadoService chamadoService;

    @GetMapping("/page")
    @Operation(summary = "Fila de chamados")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<PageResult<ChamadoVOs.Resp>> pagina(@Valid ChamadoVOs.PaginaReq req) {
        return success(chamadoService.pagina(req, getLoginUserId()));
    }

    @GetMapping("/contagens")
    @Operation(summary = "Quantidades por fila (abertos, novos, pendentes, SLA vencido, solucionados)")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<Map<String, Long>> contagens() {
        return success(chamadoService.contagens());
    }

    @GetMapping("/get")
    @Operation(summary = "Detalhe do chamado, com as ações permitidas ao usuário")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<ChamadoVOs.Resp> obter(@RequestParam("id") Long id) {
        return success(chamadoService.obter(id, getLoginUserId()));
    }

    @GetMapping("/linha-do-tempo")
    @Operation(summary = "Acompanhamentos, tarefas e soluções")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<List<ChamadoVOs.Interacao>> linhaDoTempo(@RequestParam("id") Long id) {
        return success(chamadoService.linhaDoTempo(id, getLoginUserId()));
    }

    @GetMapping("/historico")
    @Operation(summary = "Histórico de alterações")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<List<HistoricoDO>> historico(@RequestParam("id") Long id) {
        return success(chamadoService.historico(id));
    }

    @PostMapping("/create")
    @Operation(summary = "Abrir chamado (equipe técnica)")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:create')")
    public CommonResult<Long> criar(@Valid @RequestBody ChamadoVOs.CriarReq req) {
        return success(chamadoService.criar(req, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "Editar dados do chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:update')")
    public CommonResult<Boolean> atualizar(@Valid @RequestBody ChamadoVOs.AtualizarReq req) {
        chamadoService.atualizar(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Excluir chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:delete')")
    public CommonResult<Boolean> excluir(@RequestParam("id") Long id) {
        chamadoService.excluir(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/ator")
    @Operation(summary = "Incluir solicitante, observador ou técnico (pessoa ou equipe)")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:atribuir')")
    public CommonResult<Boolean> adicionarAtor(@Valid @RequestBody ChamadoVOs.AtorReq req) {
        chamadoService.adicionarAtor(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/ator")
    @Operation(summary = "Remover participante")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:atribuir')")
    public CommonResult<Boolean> removerAtor(@RequestParam("id") Long atorId) {
        chamadoService.removerAtor(atorId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/assumir")
    @Operation(summary = "O técnico logado assume o chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:atender')")
    public CommonResult<Boolean> assumir(@RequestParam("id") Long id) {
        chamadoService.assumir(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/interacao")
    @Operation(summary = "Acompanhamento, tarefa ou solução")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:atender')")
    public CommonResult<Long> interacao(@Valid @RequestBody ChamadoVOs.InteracaoReq req) {
        return success(chamadoService.adicionarInteracao(req, getLoginUserId(), true));
    }

    @PostMapping("/pendente")
    @Operation(summary = "Colocar em pendente (para o relógio)")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:atender')")
    public CommonResult<Boolean> pendente(@Valid @RequestBody ChamadoVOs.PendenteReq req) {
        chamadoService.colocarPendente(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/retomar")
    @Operation(summary = "Tirar de pendente")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:atender')")
    public CommonResult<Boolean> retomar(@RequestParam("id") Long id) {
        chamadoService.retomar(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/avaliar-solucao")
    @Operation(summary = "Aprovar (fecha) ou recusar (reabre) a solução em nome do solicitante")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:fechar')")
    public CommonResult<Boolean> avaliarSolucao(@Valid @RequestBody ChamadoVOs.AvaliarSolucaoReq req) {
        chamadoService.avaliarSolucao(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/fechar")
    @Operation(summary = "Fechar sem esperar a aprovação do solicitante")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:fechar')")
    public CommonResult<Boolean> fechar(@RequestParam("id") Long id) {
        chamadoService.fechar(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/reabrir")
    @Operation(summary = "Reabrir chamado solucionado (ou fechado, com permissão)")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:atender')")
    public CommonResult<Boolean> reabrir(@RequestParam("id") Long id) {
        chamadoService.reabrir(id, getLoginUserId());
        return success(true);
    }

}
