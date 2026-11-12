package cn.iocoder.yudao.module.chat.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.chat.service.ApuracaoChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** Apuração das conversas do chat: só leitura, com motivo e trilha de auditoria em cada consulta. */
@Tag(name = "CO-TI - Chat - Apuração")
@RestController
@RequestMapping("/chat/apuracao")
@Validated
public class ApuracaoChatController {

    private static final String APURAR = "@ss.hasPermission('chat:apurar')";

    @Resource
    private ApuracaoChatService apuracao;

    @GetMapping("/acesso")
    @Operation(summary = "Se quem está logado pode apurar (a permissão precisa estar num perfil seu)")
    public CommonResult<Boolean> acesso() {
        return success(apuracao.podeApurar(getLoginUserId()));
    }

    @GetMapping("/pessoas")
    @Operation(summary = "Pessoas para escolher, inclusive desativadas")
    @PreAuthorize(APURAR)
    public CommonResult<List<ApuracaoChatService.Pessoa>> pessoas(@RequestParam(value = "busca", required = false) String busca) {
        return success(apuracao.pessoas(busca, getLoginUserId()));
    }

    @PostMapping("/conversas")
    @Operation(summary = "Conversas com mensagens no período")
    @PreAuthorize(APURAR)
    public CommonResult<List<ApuracaoChatService.Conversa>> conversas(@Valid @RequestBody ApuracaoChatService.Filtro f) {
        return success(apuracao.conversas(f, getLoginUserId()));
    }

    @PostMapping("/mensagens")
    @Operation(summary = "Mensagens, com o texto antes de editar ou apagar")
    @PreAuthorize(APURAR)
    public CommonResult<ApuracaoChatService.Pagina> mensagens(@Valid @RequestBody ApuracaoChatService.Filtro f) {
        return success(apuracao.mensagens(f, getLoginUserId()));
    }

    @PostMapping("/exportar")
    @Operation(summary = "Planilha com as mensagens encontradas")
    @PreAuthorize(APURAR)
    public void exportar(@Valid @RequestBody ApuracaoChatService.Filtro f, HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "apuracao-chat-" + LocalDate.now() + ".xlsx", apuracao.exportar(f, getLoginUserId()));
    }

    /** O motivo vai no corpo, não na URL: assim não fica nos logs de acesso. */
    public record AnexoReq(@NotNull Long id, @NotBlank String motivo) {
    }

    @PostMapping("/anexo")
    @Operation(summary = "Baixa um anexo que ainda não expirou")
    @PreAuthorize(APURAR)
    public void anexo(@Valid @RequestBody AnexoReq req, HttpServletResponse resposta) throws IOException {
        ApuracaoChatService.Arquivo a = apuracao.anexo(req.id(), req.motivo(), getLoginUserId());
        resposta.setContentType(a.tipoMime());
        resposta.setHeader("X-Content-Type-Options", "nosniff");
        resposta.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(a.nome(), StandardCharsets.UTF_8).build().toString());
        resposta.setContentLengthLong(Files.size(a.caminho()));
        Files.copy(a.caminho(), resposta.getOutputStream());
    }

}
