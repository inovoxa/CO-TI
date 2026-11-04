package cn.iocoder.yudao.module.chat.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.PessoaRateLimiterKeyResolver;
import cn.iocoder.yudao.module.chat.controller.admin.vo.ChatVOs;
import cn.iocoder.yudao.module.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Chat interno")
@RestController
@RequestMapping("/chat")
@Validated
public class ChatController {

    private static final String USAR = "@ss.hasPermission('chat:usar')";

    @Resource
    private ChatService chatService;

    @GetMapping("/pessoas")
    @Operation(summary = "Procura pessoas para conversar")
    @PreAuthorize(USAR)
    public CommonResult<List<ChatVOs.Pessoa>> pessoas(@RequestParam(value = "busca", required = false) String busca) {
        return success(chatService.pessoas(busca, getLoginUserId()));
    }

    @PostMapping("/presenca")
    @Operation(summary = "Avisa que a tela está aberta")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> presenca() {
        chatService.presenca(getLoginUserId());
        return success(true);
    }

    @GetMapping("/conversas")
    @Operation(summary = "Minhas conversas, da mais recente para a mais antiga")
    @PreAuthorize(USAR)
    public CommonResult<List<ChatVOs.ConversaResp>> conversas() {
        return success(chatService.conversas(getLoginUserId()));
    }

    @GetMapping("/conversa")
    @Operation(summary = "Uma conversa, com os participantes")
    @PreAuthorize(USAR)
    public CommonResult<ChatVOs.ConversaResp> conversa(@RequestParam("id") Long id) {
        return success(chatService.conversa(id, getLoginUserId()));
    }

    @GetMapping("/nao-lidas")
    @Operation(summary = "Total de mensagens não lidas")
    @PreAuthorize(USAR)
    public CommonResult<Long> naoLidas() {
        return success(chatService.totalNaoLidas(getLoginUserId()));
    }

    @PostMapping("/direta")
    @Operation(summary = "Abre a conversa com uma pessoa; cria se ainda não existir")
    @PreAuthorize(USAR)
    public CommonResult<Long> direta(@Valid @RequestBody ChatVOs.DiretaReq req) {
        return success(chatService.abrirDireta(req.getUserId(), getLoginUserId()));
    }

    @PostMapping("/grupo")
    @Operation(summary = "Cria um grupo; quem cria é o administrador")
    @PreAuthorize(USAR)
    public CommonResult<Long> grupo(@Valid @RequestBody ChatVOs.GrupoReq req) {
        return success(chatService.criarGrupo(req, getLoginUserId()));
    }

    @PostMapping("/grupo/renomear")
    @Operation(summary = "Renomeia o grupo")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> renomear(@Valid @RequestBody ChatVOs.RenomearReq req) {
        chatService.renomear(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/grupo/adicionar")
    @Operation(summary = "Adiciona pessoas; elas só veem as mensagens a partir de agora")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> adicionar(@Valid @RequestBody ChatVOs.MembrosReq req) {
        chatService.adicionar(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/grupo/remover")
    @Operation(summary = "Remove uma pessoa do grupo")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> remover(@RequestParam("conversaId") Long conversaId, @RequestParam("userId") Long userId) {
        chatService.remover(conversaId, userId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/grupo/sair")
    @Operation(summary = "Sai do grupo")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> sair(@RequestParam("conversaId") Long conversaId) {
        chatService.sair(conversaId, getLoginUserId());
        return success(true);
    }

    @GetMapping("/mensagens")
    @Operation(summary = "Histórico: as mais recentes, antes de um número ou depois de um número")
    @PreAuthorize(USAR)
    public CommonResult<List<ChatVOs.MensagemResp>> mensagens(@RequestParam("conversaId") Long conversaId,
                                                              @RequestParam(value = "antesDe", required = false) Long antesDe,
                                                              @RequestParam(value = "depoisDe", required = false) Long depoisDe,
                                                              @RequestParam(value = "limite", required = false) Integer limite) {
        return success(chatService.mensagens(conversaId, antesDe, depoisDe, limite, getLoginUserId()));
    }

    @PostMapping("/enviar")
    @Operation(summary = "Envia uma mensagem")
    @PreAuthorize(USAR)
    @RateLimiter(time = 60, count = 120, keyResolver = PessoaRateLimiterKeyResolver.class, message = "Muitas mensagens em pouco tempo. Aguarde um instante.")
    public CommonResult<ChatVOs.MensagemResp> enviar(@Valid @RequestBody ChatVOs.EnviarReq req) {
        return success(chatService.enviar(req, getLoginUserId()));
    }

    @PostMapping("/editar")
    @Operation(summary = "Edita a própria mensagem")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> editar(@Valid @RequestBody ChatVOs.EditarReq req) {
        chatService.editar(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/apagar")
    @Operation(summary = "Apaga para todos a própria mensagem; o administrador do grupo também pode")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> apagar(@RequestParam("mensagemId") Long mensagemId) {
        chatService.apagar(mensagemId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/lida")
    @Operation(summary = "Marca como lido até um número")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> lida(@Valid @RequestBody ChatVOs.LidaReq req) {
        chatService.marcarLida(req, getLoginUserId());
        return success(true);
    }

    @GetMapping("/buscar")
    @Operation(summary = "Busca nas mensagens das minhas conversas")
    @PreAuthorize(USAR)
    public CommonResult<List<ChatVOs.Achado>> buscar(@RequestParam("texto") String texto,
                                                     @RequestParam(value = "conversaId", required = false) Long conversaId) {
        return success(chatService.buscar(texto, conversaId, getLoginUserId()));
    }

    @PostMapping("/anexo")
    @Operation(summary = "Envia um arquivo, que entra na próxima mensagem")
    @PreAuthorize(USAR)
    @RateLimiter(time = 60, count = 30, keyResolver = PessoaRateLimiterKeyResolver.class, message = "Muitas mensagens em pouco tempo. Aguarde um instante.")
    public CommonResult<ChatVOs.AnexoResp> anexo(@RequestParam("conversaId") Long conversaId,
                                                 @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return success(chatService.anexar(conversaId, arquivo, getLoginUserId()));
    }

    @GetMapping("/anexo/baixar")
    @Operation(summary = "Baixa um anexo; só quem participa da conversa")
    @PreAuthorize(USAR)
    public void baixar(@RequestParam("id") Long id, HttpServletResponse resposta) throws IOException {
        ChatService.Baixa b = chatService.baixar(id, getLoginUserId());
        // sempre como download, e sem o navegador adivinhar o tipo: arquivo enviado por alguém não roda no portal
        resposta.setContentType(b.tipoMime());
        resposta.setHeader("X-Content-Type-Options", "nosniff");
        resposta.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(b.nome(), StandardCharsets.UTF_8).build().toString());
        resposta.setContentLengthLong(Files.size(b.arquivo()));
        Files.copy(b.arquivo(), resposta.getOutputStream());
    }

}
