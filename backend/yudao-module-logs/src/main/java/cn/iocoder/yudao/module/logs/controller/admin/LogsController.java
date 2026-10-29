package cn.iocoder.yudao.module.logs.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.logs.config.LogsProperties;
import cn.iocoder.yudao.module.logs.controller.admin.vo.LogsVOs;
import cn.iocoder.yudao.module.logs.service.ConsultaLogsService;
import cn.iocoder.yudao.module.logs.service.GravadorLogs;
import cn.iocoder.yudao.module.logs.service.PainelLogsService;
import cn.iocoder.yudao.module.logs.service.RecepcaoLogs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CO-TI - Logs centralizados")
@RestController
@RequestMapping("/logs")
@Validated
public class LogsController {

    @Resource
    private ConsultaLogsService consultaLogsService;
    @Resource
    private PainelLogsService painelLogsService;
    @Resource
    private RecepcaoLogs recepcaoLogs;
    @Resource
    private GravadorLogs gravadorLogs;
    @Resource
    private LogsProperties props;

    // ================================================================================== consulta

    @PostMapping("/mensagens")
    @Operation(summary = "Mensagens do filtro, mais recentes primeiro")
    @PreAuthorize("@ss.hasPermission('logs:consultar')")
    public CommonResult<LogsVOs.Mensagens> mensagens(@Valid @RequestBody LogsVOs.MensagensReq req) {
        return success(consultaLogsService.mensagens(req));
    }

    @PostMapping("/contagem")
    @Operation(summary = "Quantas mensagens (ou valores distintos) no período e no período anterior")
    @PreAuthorize("@ss.hasPermission('logs:consultar')")
    public CommonResult<LogsVOs.Contagem> contagem(@Valid @RequestBody LogsVOs.ContagemReq req) {
        return success(consultaLogsService.contagem(req));
    }

    @PostMapping("/ranking")
    @Operation(summary = "Valores mais frequentes de um campo")
    @PreAuthorize("@ss.hasPermission('logs:consultar')")
    public CommonResult<LogsVOs.Ranking> ranking(@Valid @RequestBody LogsVOs.RankingReq req) {
        return success(consultaLogsService.ranking(req));
    }

    @PostMapping("/histograma")
    @Operation(summary = "Mensagens (ou a soma de um campo, ex.: bytes) ao longo do tempo")
    @PreAuthorize("@ss.hasPermission('logs:consultar')")
    public CommonResult<LogsVOs.Histograma> histograma(@Valid @RequestBody LogsVOs.Filtro filtro,
                                                       @RequestParam(value = "soma", required = false) String soma) {
        return success(consultaLogsService.histograma(filtro, soma));
    }

    @PostMapping("/campos")
    @Operation(summary = "Campos presentes nas mensagens recentes do filtro")
    @PreAuthorize("@ss.hasPermission('logs:consultar')")
    public CommonResult<List<LogsVOs.Item>> campos(@Valid @RequestBody LogsVOs.Filtro filtro) {
        return success(consultaLogsService.campos(filtro));
    }

    // ================================================================================== painéis

    @GetMapping("/paineis")
    @Operation(summary = "Painéis (abas) com seus quadros")
    @PreAuthorize("@ss.hasPermission('logs:consultar')")
    public CommonResult<List<LogsVOs.Painel>> paineis() {
        return success(painelLogsService.listar());
    }

    @PostMapping("/painel")
    @Operation(summary = "Cria ou altera um painel")
    @PreAuthorize("@ss.hasPermission('logs:painel:editar')")
    public CommonResult<Long> salvarPainel(@Valid @RequestBody LogsVOs.Painel req) {
        return success(painelLogsService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/painel")
    @Operation(summary = "Exclui um painel")
    @PreAuthorize("@ss.hasPermission('logs:painel:editar')")
    public CommonResult<Boolean> excluirPainel(@RequestParam("id") Long id) {
        painelLogsService.excluir(id, getLoginUserId());
        return success(true);
    }

    @PutMapping("/paineis/ordem")
    @Operation(summary = "Ordem das abas")
    @PreAuthorize("@ss.hasPermission('logs:painel:editar')")
    public CommonResult<Boolean> ordenar(@RequestBody List<Long> ids) {
        painelLogsService.ordenar(ids);
        return success(true);
    }

    // ================================================================================== fontes

    @GetMapping("/situacao")
    @Operation(summary = "Entradas de rede, fila de gravação, volume e tamanho")
    @PreAuthorize("@ss.hasPermission('logs:fontes')")
    public CommonResult<LogsVOs.Situacao> situacao() {
        LogsVOs.Situacao s = new LogsVOs.Situacao();
        for (RecepcaoLogs.Entrada e : recepcaoLogs.entradas()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", e.nome());
            m.put("protocolo", e.protocolo());
            m.put("porta", e.porta());
            m.put("portaPublica", e.portaPublica());
            m.put("ativa", e.ativa());
            m.put("erro", e.erro());
            m.put("recebidas", e.recebidas());
            m.put("recusadas", e.recusadas());
            s.getEntradas().add(m);
        }
        GravadorLogs.Contadores c = gravadorLogs.contadores();
        s.setGravadas(c.gravadas());
        s.setDescartadas(c.descartadas());
        s.setInvalidas(c.invalidas());
        s.setFalhas(c.falhas());
        s.setFila(gravadorLogs.pendentes());
        s.setRetencaoDias(props.getRetencaoDias());
        s.setUltimas24h(consultaLogsService.ultimas24hPorFluxo());
        s.setTamanho(consultaLogsService.tamanho());
        return success(s);
    }

    @GetMapping("/origens")
    @Operation(summary = "Quem mandou logs nas últimas 24 h")
    @PreAuthorize("@ss.hasPermission('logs:fontes')")
    public CommonResult<List<LogsVOs.Origem>> origens() {
        return success(consultaLogsService.origens());
    }

}
