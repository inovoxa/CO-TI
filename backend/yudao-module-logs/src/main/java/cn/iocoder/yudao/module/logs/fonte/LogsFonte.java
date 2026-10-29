package cn.iocoder.yudao.module.logs.fonte;

import cn.iocoder.yudao.module.logs.controller.admin.vo.LogsVOs;
import cn.iocoder.yudao.module.logs.service.ConsultaLogsService;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.ListaVO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.LogVO;
import cn.iocoder.yudao.module.system.service.monitor.fonte.MonitorFontes;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * Logs reais para o assistente de IA e as telas do Centro de Operações (no lugar da simulação): últimas 24 h, com
 * filtro de nível e texto.
 */
@Primary
@Component
public class LogsFonte implements MonitorFontes.Logs {

    @Resource
    private ConsultaLogsService consultaLogsService;

    @Override
    public ListaVO<LogVO> buscarLogs(Long unidadeId, String nivel, String busca, int limite) {
        LogsVOs.Filtro f = new LogsVOs.Filtro();
        f.setPeriodo("1d");
        f.setConsulta(busca == null || busca.isBlank() ? null : busca);
        f.setFiltroGeral(switch (nivel == null ? "todos" : nivel) {
            case "critico" -> "nivel:<=2";
            case "erro" -> "nivel:3";
            case "aviso" -> "nivel:4";
            case "info" -> "nivel:>=5";
            default -> null;
        });
        LogsVOs.MensagensReq req = new LogsVOs.MensagensReq();
        req.setFiltro(f);
        req.setTamanho(Math.max(1, Math.min(limite, 200)));
        LogsVOs.Mensagens m = consultaLogsService.mensagens(req);

        ListaVO<LogVO> out = new ListaVO<>();
        out.setTotal(m.getTotal());
        for (LogsVOs.Mensagem x : m.getLista()) {
            LogVO v = new LogVO();
            v.setTs(x.getMomento().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            v.setOrigem(x.getOrigem());
            v.setNivel(x.getNivel() <= 2 ? "critico" : x.getNivel() == 3 ? "erro" : x.getNivel() == 4 ? "aviso" : "info");
            v.setMensagem(x.getMensagem());
            out.getLista().add(v);
        }
        consultaLogsService.ultimas24hPorFluxo().forEach(out.getContagens()::put);
        return out;
    }

}
