package cn.iocoder.yudao.module.calendario.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.calendario.service.CalendarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Calendário")
@RestController
@RequestMapping("/calendario")
@Validated
public class CalendarioController {

    private static final String USAR = "@ss.hasPermission('calendario:usar')";

    @Resource
    private CalendarioService calendario;

    /** Data de nascimento de quem está logado: fica em Meu perfil, que todo mundo acessa. */
    @GetMapping("/meu-nascimento")
    @Operation(summary = "Minha data de nascimento")
    public CommonResult<CalendarioService.Nascimento> meuNascimento() {
        return success(calendario.meuNascimento(getLoginUserId()));
    }

    @PutMapping("/meu-nascimento")
    @Operation(summary = "Salva minha data de nascimento e se ela aparece no calendário")
    public CommonResult<Boolean> salvarNascimento(@RequestBody CalendarioService.NascimentoReq req) {
        calendario.salvarNascimento(getLoginUserId(), req);
        return success(true);
    }

    @GetMapping("/aniversariantes")
    @Operation(summary = "Aniversariantes do ano ou do mês, de uma unidade e das que ficam abaixo dela")
    @PreAuthorize(USAR)
    public CommonResult<List<CalendarioService.Aniversariante>> aniversariantes(
            @RequestParam("ano") int ano, @RequestParam(value = "mes", required = false) Integer mes,
            @RequestParam(value = "deptId", required = false) Long deptId) {
        return success(calendario.aniversariantes(ano, mes, deptId, getLoginUserId()));
    }

    @GetMapping("/aniversariantes/exportar")
    @Operation(summary = "Planilha dos aniversariantes (dia e mês)")
    @PreAuthorize(USAR)
    public void exportarAniversariantes(@RequestParam("ano") int ano, @RequestParam(value = "mes", required = false) Integer mes,
                                        @RequestParam(value = "deptId", required = false) Long deptId,
                                        HttpServletResponse resposta) throws IOException {
        Planilha.responder(resposta, "aniversariantes.xlsx", calendario.exportarAniversariantes(ano, mes, deptId, getLoginUserId()));
    }

    @GetMapping("/eventos")
    @Operation(summary = "Meus compromissos no período")
    @PreAuthorize(USAR)
    public CommonResult<List<CalendarioService.Evento>> eventos(
            @RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return success(calendario.eventos(getLoginUserId(), de, ate));
    }

    @PostMapping("/eventos")
    @Operation(summary = "Novo compromisso na minha agenda")
    @PreAuthorize(USAR)
    public CommonResult<Long> criar(@Valid @RequestBody CalendarioService.EventoReq req) {
        return success(calendario.criar(getLoginUserId(), req));
    }

    @PutMapping("/eventos/{id}")
    @Operation(summary = "Altera um compromisso meu")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> alterar(@PathVariable("id") Long id, @Valid @RequestBody CalendarioService.EventoReq req) {
        calendario.alterar(getLoginUserId(), id, req);
        return success(true);
    }

    @DeleteMapping("/eventos/{id}")
    @Operation(summary = "Exclui um compromisso meu")
    @PreAuthorize(USAR)
    public CommonResult<Boolean> excluir(@PathVariable("id") Long id) {
        calendario.excluir(getLoginUserId(), id);
        return success(true);
    }

}
