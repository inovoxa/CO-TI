package cn.iocoder.yudao.module.system.controller.admin.monitor;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.*;
import cn.iocoder.yudao.module.system.service.monitor.fonte.MonitorFontes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource; // no branch master (JDK 8) troque por javax.annotation.Resource
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Uma rota por módulo do Centro de Operações. Cada uma tem a sua permissão, que é cadastrada no menu
 * correspondente (veja backend/sql/monitor_menus.sql). Assim cada perfil vê só os módulos que lhe cabem.
 *
 * <p>O controller não sabe de onde vêm os dados: ele fala com as interfaces de {@link MonitorFontes}.
 */
@Tag(name = "Gestão - Centro de Operações (módulos)")
@RestController
@RequestMapping("/system/monitor")
@Validated
public class MonitorModulosController {

    private static final int LIMITE = 200;

    @Resource
    private MonitorFontes.Chamados chamados;
    @Resource
    private MonitorFontes.Links links;
    @Resource
    private MonitorFontes.Inventario inventario;
    @Resource
    private MonitorFontes.Logs logs;
    @Resource
    private MonitorFontes.Cameras cameras;

    @Value("${monitor.urls.element:}")
    private String urlElement;
    @Value("${monitor.urls.grafana:}")
    private String urlGrafana;

    // ---------------- Chamados ----------------
    @GetMapping("/chamados")
    @Operation(summary = "Fila de chamados")
    @PreAuthorize("@ss.hasPermission('system:monitor-chamados:query')")
    public CommonResult<ListaVO<ChamadoVO>> listarChamados(@RequestParam(required = false) Long unidadeId,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String busca) {
        return success(chamados.listarChamados(unidadeId, status, busca, LIMITE));
    }

    @PostMapping("/chamados")
    @Operation(summary = "Abrir chamado")
    @PreAuthorize("@ss.hasPermission('system:monitor-chamados:create')")
    public CommonResult<ChamadoVO> abrirChamado(@RequestBody ChamadoNovoReqVO req) {
        if (req.getTitulo() == null || req.getTitulo().trim().isEmpty() || req.getTitulo().length() > 200) {
            return CommonResult.error(400, "Informe um título de até 200 caracteres.");
        }
        req.setTitulo(req.getTitulo().trim());
        return success(chamados.abrirChamado(req, "usuario#" + SecurityFrameworkUtils.getLoginUserId(), ""));
    }

    // ---------------- Links e serviços ----------------
    @GetMapping("/links")
    @Operation(summary = "Monitores de links e serviços")
    @PreAuthorize("@ss.hasPermission('system:monitor-links:query')")
    public CommonResult<ListaVO<LinkVO>> listarLinks(@RequestParam(required = false) Long unidadeId,
                                                     @RequestParam(required = false) String filtro) {
        return success(links.listarLinks(unidadeId, filtro, LIMITE));
    }

    // ---------------- Inventário ----------------
    @GetMapping("/inventario")
    @Operation(summary = "Inventário de equipamentos")
    @PreAuthorize("@ss.hasPermission('system:monitor-inventario:query')")
    public CommonResult<ListaVO<EquipamentoVO>> listarEquipamentos(@RequestParam(required = false) Long unidadeId,
                                                                   @RequestParam(required = false) String filtro,
                                                                   @RequestParam(required = false) String busca) {
        return success(inventario.listarEquipamentos(unidadeId, filtro, busca, LIMITE));
    }

    // ---------------- Logs ----------------
    @GetMapping("/logs")
    @Operation(summary = "Busca de logs")
    @PreAuthorize("@ss.hasPermission('logs:consultar')")
    public CommonResult<ListaVO<LogVO>> buscarLogs(@RequestParam(required = false) Long unidadeId,
                                                   @RequestParam(required = false) String nivel,
                                                   @RequestParam(required = false) String busca) {
        return success(logs.buscarLogs(unidadeId, nivel, busca, LIMITE));
    }

    // ---------------- Câmeras ----------------
    @GetMapping("/cameras")
    @Operation(summary = "Câmeras, contagens e eventos de acesso")
    @PreAuthorize("@ss.hasPermission('system:monitor-cameras:query')")
    public CommonResult<CameraResumoVO> resumoCameras(@RequestParam(required = false) Long unidadeId) {
        return success(cameras.resumoCameras(unidadeId, 60));
    }

    // ---------------- Endereços das ferramentas embutidas ----------------
    @GetMapping("/config")
    @Operation(summary = "Endereços do Element (mensagens) e do Grafana (plataforma)")
    @PreAuthorize("@ss.hasPermission('system:monitor-screen:query')")
    public CommonResult<Map<String, String>> config() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("element", urlElement);
        m.put("grafana", urlGrafana);
        return success(m);
    }

}
