package cn.iocoder.yudao.module.atendimento.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.service.*;
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

@Tag(name = "CO-TI - Atendimento")
@RestController
@RequestMapping("/atendimento")
@Validated
public class AtendimentoController {

    private static final String Q = "@ss.hasPermission('atendimento:conversa:query')";

    @Resource
    private ConversaService conversaService;
    @Resource
    private ContatoService contatoService;
    @Resource
    private CaixaService caixaService;
    @Resource
    private EtiquetaService etiquetaService;
    @Resource
    private AgenteService agenteService;
    @Resource
    private EnvioService envioService;
    @Resource
    private Acesso acesso;
    @Resource
    private CanalConfigService canalConfigService;
    @Resource
    private ModeloService modeloService;
    @Resource
    private MacroService macroService;
    @Resource
    private cn.iocoder.yudao.module.itsm.service.SlaService slaService;
    @Resource
    private AutomacaoService automacaoService;

    // ================= conversas =================

    @GetMapping("/conversa/page")
    @Operation(summary = "Fila de conversas (minhas, não atribuídas, todas)")
    @PreAuthorize(Q)
    public CommonResult<PageResult<AtendimentoVOs.ConversaResumo>> conversas(@Valid AtendimentoVOs.FiltroConversa f) {
        return success(conversaService.pagina(f, getLoginUserId()));
    }

    @GetMapping("/conversa/contagens")
    @PreAuthorize(Q)
    public CommonResult<Map<String, Long>> contagens(@RequestParam(value = "caixaId", required = false) Long caixaId) {
        return success(conversaService.contagens(caixaId, getLoginUserId()));
    }

    @GetMapping("/conversa/get")
    @PreAuthorize(Q)
    public CommonResult<AtendimentoVOs.ConversaResumo> conversa(@RequestParam("id") Long id) {
        return success(conversaService.obter(id, getLoginUserId()));
    }

    @GetMapping("/conversa/mensagens")
    @Operation(summary = "Mensagens (50 por vez; antesDe pagina para trás)")
    @PreAuthorize(Q)
    public CommonResult<List<AtendimentoVOs.Mensagem>> mensagens(@RequestParam("id") Long id,
                                                                 @RequestParam(value = "antesDe", required = false) Long antesDe) {
        return success(conversaService.mensagens(id, antesDe, getLoginUserId()));
    }

    @GetMapping("/conversa/lateral")
    @Operation(summary = "Contato, chamados dele e conversas anteriores")
    @PreAuthorize(Q)
    public CommonResult<AtendimentoVOs.Lateral> lateral(@RequestParam("id") Long id) {
        return success(conversaService.lateral(id, getLoginUserId()));
    }

    @PostMapping("/conversa/responder")
    @Operation(summary = "Responder ao contato ou escrever nota interna")
    @PreAuthorize(Q)
    public CommonResult<Long> responder(@Valid @RequestBody AtendimentoVOs.ResponderReq req) {
        return success(conversaService.responder(req, getLoginUserId()));
    }

    @PostMapping("/conversa/atribuir")
    @PreAuthorize(Q)
    public CommonResult<Boolean> atribuir(@Valid @RequestBody AtendimentoVOs.AtribuirReq req) {
        conversaService.atribuir(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/conversa/status")
    @Operation(summary = "Resolver, reabrir, adiar ou deixar pendente")
    @PreAuthorize(Q)
    public CommonResult<Boolean> status(@Valid @RequestBody AtendimentoVOs.StatusReq req) {
        conversaService.mudarStatus(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/conversa/etiquetas")
    @PreAuthorize(Q)
    public CommonResult<Boolean> etiquetas(@Valid @RequestBody AtendimentoVOs.EtiquetasReq req) {
        conversaService.etiquetas(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/conversa/prioridade")
    @PreAuthorize(Q)
    public CommonResult<Boolean> prioridade(@Valid @RequestBody AtendimentoVOs.PrioridadeReq req) {
        conversaService.prioridade(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/conversa/lida")
    @PreAuthorize(Q)
    public CommonResult<Boolean> lida(@RequestParam("id") Long id) {
        conversaService.marcarLida(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/conversa/abrir-chamado")
    @Operation(summary = "Abrir chamado no Service Desk a partir da conversa")
    @PreAuthorize(Q + " and @ss.hasPermission('itsm:chamado:create')")
    public CommonResult<Long> abrirChamado(@Valid @RequestBody AtendimentoVOs.AbrirChamadoReq req,
                                           @RequestParam(value = "avisarContato", defaultValue = "true") boolean avisar) {
        return success(conversaService.abrirChamado(req, avisar, getLoginUserId()));
    }

    @PostMapping("/conversa/modelo")
    @Operation(summary = "Mandar um modelo aprovado (WhatsApp, depois das 24 h)")
    @PreAuthorize(Q)
    public CommonResult<Long> modelo(@Valid @RequestBody AtendimentoVOs.ModeloReq req) {
        return success(modeloService.enviar(req, getLoginUserId()));
    }

    @GetMapping("/modelo/list")
    @Operation(summary = "Modelos aprovados da caixa")
    @PreAuthorize(Q)
    public CommonResult<List<AtendimentoVOs.Modelo>> modelos(@RequestParam("caixaId") Long caixaId) {
        return success(modeloService.listar(caixaId, getLoginUserId(), false));
    }

    @PostMapping("/mensagem/reenviar")
    @Operation(summary = "Tentar de novo uma mensagem que falhou")
    @PreAuthorize(Q)
    public CommonResult<Boolean> reenviar(@RequestParam("conversaId") Long conversaId, @RequestParam("mensagemId") Long mensagemId) {
        acesso.exigir(conversaService.existente(conversaId), getLoginUserId());
        envioService.reenviar(mensagemId);
        return success(true);
    }

    // ================= macros =================

    @GetMapping("/macro/list")
    @Operation(summary = "Minhas macros e as para todos")
    @PreAuthorize(Q)
    public CommonResult<List<AtendimentoVOs.Macro>> macros() {
        return success(macroService.listar(getLoginUserId()));
    }

    @PostMapping("/macro/save")
    @PreAuthorize(Q)
    public CommonResult<Long> salvarMacro(@Valid @RequestBody AtendimentoVOs.Macro req) {
        return success(macroService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/macro/delete")
    @PreAuthorize(Q)
    public CommonResult<Boolean> excluirMacro(@RequestParam("id") Long id) {
        macroService.excluir(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/macro/executar")
    @Operation(summary = "Executar a macro na conversa")
    @PreAuthorize(Q)
    public CommonResult<List<String>> executarMacro(@Valid @RequestBody AtendimentoVOs.MacroExecReq req) {
        return success(macroService.executar(req.getMacroId(), req.getConversaId(), getLoginUserId()));
    }

    // ================= automações =================

    @GetMapping("/automacao/list")
    @PreAuthorize("@ss.hasPermission('atendimento:automacao:update')")
    public CommonResult<List<AtendimentoVOs.Automacao>> automacoes() {
        return success(automacaoService.listar());
    }

    @PostMapping("/automacao/save")
    @PreAuthorize("@ss.hasPermission('atendimento:automacao:update')")
    public CommonResult<Long> salvarAutomacao(@Valid @RequestBody AtendimentoVOs.Automacao req) {
        return success(automacaoService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/automacao/delete")
    @PreAuthorize("@ss.hasPermission('atendimento:automacao:update')")
    public CommonResult<Boolean> excluirAutomacao(@RequestParam("id") Long id) {
        automacaoService.excluir(id, getLoginUserId());
        return success(true);
    }

    // ================= atendente =================

    @PostMapping("/agente/disponibilidade")
    @PreAuthorize(Q)
    public CommonResult<Boolean> disponibilidade(@RequestParam("valor") String valor) {
        agenteService.definir(getLoginUserId(), valor);
        return success(true);
    }

    @PostMapping("/agente/sinal")
    @Operation(summary = "Sinal de vida da tela de conversas (a cada minuto)")
    @PreAuthorize(Q)
    public CommonResult<String> sinal() {
        return success(agenteService.sinal(getLoginUserId()));
    }

    // ================= caixas =================

    @GetMapping("/caixa/minhas")
    @Operation(summary = "Caixas que eu atendo (todas, para a supervisão)")
    @PreAuthorize(Q)
    public CommonResult<List<AtendimentoVOs.Caixa>> minhasCaixas() {
        Long u = getLoginUserId();
        return success(caixaService.minhas(u, acesso.veTodas(u)));
    }

    @GetMapping("/caixa/list")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<List<AtendimentoVOs.Caixa>> caixas() {
        return success(caixaService.listar());
    }

    @PostMapping("/caixa/save")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<Long> salvarCaixa(@Valid @RequestBody AtendimentoVOs.Caixa req) {
        return success(caixaService.salvar(req));
    }

    @PostMapping("/caixa/credenciais")
    @Operation(summary = "Gerar token de entrada e segredo do webhook (aparecem só agora)")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<AtendimentoVOs.Credenciais> credenciais(@RequestParam("id") Long id) {
        return success(caixaService.gerarCredenciais(id, getLoginUserId()));
    }

    @GetMapping("/caixa/calendarios")
    @Operation(summary = "Calendários do Service Desk para o horário da caixa")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<List<Map<String, Object>>> calendarios() {
        return success(slaService.listarCalendarios().stream().map(c -> Map.<String, Object>of("id", c.getId(), "nome", c.getNome())).toList());
    }

    @GetMapping("/caixa/canal")
    @Operation(summary = "Configuração do canal (WhatsApp oficial, Instagram, Telegram), sem os segredos")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<AtendimentoVOs.CanalConfigResp> canal(@RequestParam("id") Long id) {
        return success(canalConfigService.obter(id));
    }

    @PostMapping("/caixa/canal")
    @Operation(summary = "Gravar a configuração do canal (segredo em branco mantém o atual)")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<AtendimentoVOs.CanalConfigResp> salvarCanal(@Valid @RequestBody AtendimentoVOs.CanalConfig req) {
        return success(canalConfigService.salvar(req, getLoginUserId()));
    }

    @PostMapping("/caixa/canal/testar")
    @Operation(summary = "Testar a conexão e ligar o recebimento (assinatura do app / webhook do Telegram)")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<AtendimentoVOs.CanalConfigResp> testarCanal(@RequestParam("id") Long id) {
        return success(canalConfigService.testar(id, getLoginUserId()));
    }

    @PostMapping("/caixa/modelos/sincronizar")
    @Operation(summary = "Buscar os modelos de mensagem da conta do WhatsApp")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<Integer> sincronizarModelos(@RequestParam("id") Long id) {
        return success(modeloService.sincronizar(id));
    }

    @GetMapping("/caixa/modelos")
    @Operation(summary = "Todos os modelos da caixa, com a situação na Meta")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<List<AtendimentoVOs.Modelo>> modelosDaCaixa(@RequestParam("id") Long id) {
        return success(modeloService.listar(id, getLoginUserId(), true));
    }

    @DeleteMapping("/caixa/delete")
    @PreAuthorize("@ss.hasPermission('atendimento:caixa:update')")
    public CommonResult<Boolean> excluirCaixa(@RequestParam("id") Long id) {
        caixaService.excluir(id);
        return success(true);
    }

    // ================= contatos =================

    @GetMapping("/contato/page")
    @PreAuthorize("@ss.hasPermission('atendimento:contato:query')")
    public CommonResult<PageResult<AtendimentoVOs.Contato>> contatos(@Valid AtendimentoVOs.FiltroContato f) {
        return success(contatoService.pagina(f));
    }

    @GetMapping("/contato/get")
    @PreAuthorize("@ss.hasPermission('atendimento:contato:query')")
    public CommonResult<AtendimentoVOs.Contato> contato(@RequestParam("id") Long id) {
        return success(contatoService.obter(id));
    }

    @PostMapping("/contato/save")
    @PreAuthorize("@ss.hasPermission('atendimento:contato:update')")
    public CommonResult<Long> salvarContato(@Valid @RequestBody AtendimentoVOs.Contato req) {
        return success(contatoService.salvar(req));
    }

    // ================= etiquetas e respostas prontas =================

    @GetMapping("/etiqueta/list")
    @PreAuthorize("@ss.hasAnyPermissions('atendimento:conversa:query', 'atendimento:etiqueta:update')")
    public CommonResult<List<AtendimentoVOs.Etiqueta>> etiquetas() {
        return success(etiquetaService.etiquetas());
    }

    @PostMapping("/etiqueta/save")
    @PreAuthorize("@ss.hasPermission('atendimento:etiqueta:update')")
    public CommonResult<Long> salvarEtiqueta(@Valid @RequestBody AtendimentoVOs.Etiqueta req) {
        return success(etiquetaService.salvarEtiqueta(req));
    }

    @DeleteMapping("/etiqueta/delete")
    @PreAuthorize("@ss.hasPermission('atendimento:etiqueta:update')")
    public CommonResult<Boolean> excluirEtiqueta(@RequestParam("id") Long id) {
        etiquetaService.excluirEtiqueta(id);
        return success(true);
    }

    @GetMapping("/resposta/list")
    @PreAuthorize("@ss.hasAnyPermissions('atendimento:conversa:query', 'atendimento:etiqueta:update')")
    public CommonResult<List<AtendimentoVOs.RespostaPronta>> respostas() {
        return success(etiquetaService.respostas());
    }

    @PostMapping("/resposta/save")
    @PreAuthorize("@ss.hasPermission('atendimento:etiqueta:update')")
    public CommonResult<Long> salvarResposta(@Valid @RequestBody AtendimentoVOs.RespostaPronta req) {
        return success(etiquetaService.salvarResposta(req));
    }

    @DeleteMapping("/resposta/delete")
    @PreAuthorize("@ss.hasPermission('atendimento:etiqueta:update')")
    public CommonResult<Boolean> excluirResposta(@RequestParam("id") Long id) {
        etiquetaService.excluirResposta(id);
        return success(true);
    }

}
