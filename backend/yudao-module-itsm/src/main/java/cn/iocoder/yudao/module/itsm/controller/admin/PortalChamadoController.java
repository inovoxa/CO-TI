package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.SlaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.CategoriaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.InteracaoDO;
import cn.iocoder.yudao.module.itsm.service.CadastroService;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * Autoatendimento: qualquer usuário logado abre e acompanha os próprios chamados.
 * Não há permissão de menu aqui de propósito; o serviço confere a participação em cada chamado.
 */
@Tag(name = "CO-TI - Meus chamados (autoatendimento)")
@RestController
@RequestMapping("/itsm/portal")
@Validated
public class PortalChamadoController {

    @Resource
    private ChamadoService chamadoService;
    @Resource
    private CadastroService cadastroService;

    @Data
    public static class CategoriaPortal {
        private Long id;
        private Long parentId;
        private String nome;
        private String nomeCompleto;
        private String descricao;
        private Boolean paraIncidente;
        private Boolean paraRequisicao;
    }

    @GetMapping("/categorias")
    @Operation(summary = "Categorias visíveis no portal")
    public CommonResult<List<CategoriaPortal>> categorias() {
        return success(cadastroService.listarCategorias(true, true).stream().map(c -> {
            CategoriaPortal p = new CategoriaPortal();
            p.setId(c.getId());
            p.setParentId(c.getParentId());
            p.setNome(c.getNome());
            p.setNomeCompleto(c.getNomeCompleto());
            p.setDescricao(c.getDescricao());
            p.setParaIncidente(c.getParaIncidente());
            p.setParaRequisicao(c.getParaRequisicao());
            return p;
        }).toList());
    }

    @PostMapping("/abrir")
    @Operation(summary = "Abrir chamado em meu nome")
    public CommonResult<Long> abrir(@Valid @RequestBody ChamadoVOs.AbrirReq req) {
        return success(chamadoService.abrirPortal(req, getLoginUserId()));
    }

    @GetMapping("/meus")
    @Operation(summary = "Chamados em que sou solicitante ou observador")
    public CommonResult<PageResult<ChamadoVOs.Resp>> meus(@Valid PageParam pagina,
                                                         @RequestParam(value = "somenteAbertos", defaultValue = "false") boolean somenteAbertos) {
        return success(chamadoService.meus(pagina, getLoginUserId(), somenteAbertos));
    }

    @GetMapping("/get")
    @Operation(summary = "Detalhe de um chamado meu")
    public CommonResult<ChamadoVOs.Resp> obter(@RequestParam("id") Long id) {
        return success(chamadoService.obterComoParticipante(id, getLoginUserId()));
    }

    @GetMapping("/linha-do-tempo")
    @Operation(summary = "Linha do tempo (sem itens privados)")
    public CommonResult<List<ChamadoVOs.Interacao>> linhaDoTempo(@RequestParam("id") Long id) {
        return success(chamadoService.linhaDoTempo(id, getLoginUserId()));
    }

    @PostMapping("/acompanhamento")
    @Operation(summary = "Escrever no chamado (num solucionado, recusa a solução e reabre)")
    public CommonResult<Long> acompanhamento(@Valid @RequestBody ChamadoVOs.InteracaoReq req) {
        req.setTipo(InteracaoDO.ACOMPANHAMENTO);
        req.setPrivado(false);
        return success(chamadoService.adicionarInteracao(req, getLoginUserId(), false));
    }

    @PostMapping("/avaliar-solucao")
    @Operation(summary = "Aprovar (fecha) ou recusar (reabre) a solução")
    public CommonResult<Boolean> avaliarSolucao(@Valid @RequestBody ChamadoVOs.AvaliarSolucaoReq req) {
        chamadoService.avaliarSolucao(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/satisfacao")
    @Operation(summary = "Responder a pesquisa de satisfação (nota de 1 a 5)")
    public CommonResult<Boolean> satisfacao(@Valid @RequestBody SlaVOs.SatisfacaoReq req) {
        chamadoService.responderPesquisa(req.getChamadoId(), req.getNota(), req.getComentario(), getLoginUserId());
        return success(true);
    }

}
