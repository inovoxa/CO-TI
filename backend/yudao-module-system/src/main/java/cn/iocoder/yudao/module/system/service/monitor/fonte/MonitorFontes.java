package cn.iocoder.yudao.module.system.service.monitor.fonte;

import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.*;

import java.util.Map;

/**
 * Contratos das "fontes" de cada módulo. As telas e o controller só conhecem estas interfaces.
 *
 * <p>Hoje todas são atendidas por {@link FontesSimuladas}. Para ligar uma ferramenta real, crie uma classe
 * que implemente só a interface daquele módulo e marque-a com {@code @Primary}; o restante continua simulado.
 * Exemplos: {@code GlpiChamados implements MonitorFontes.Chamados} (API REST do GLPI),
 * {@code UptimeKumaLinks implements MonitorFontes.Links} (endpoint /metrics), {@code OcsInventario},
 * {@code GraylogLogs} (API de busca), {@code FrigateCameras} (eventos via MQTT e API HTTP).
 *
 * <p>Parâmetro {@code unidadeId}: {@code null} = todas as unidades.
 */
public final class MonitorFontes {

    private MonitorFontes() {
    }

    public interface Chamados {
        /** status: abertos | Novo | Em atendimento | Pendente | Solucionado | sla | auto | todos */
        ListaVO<ChamadoVO> listarChamados(Long unidadeId, String status, String busca, int limite);

        ChamadoVO abrirChamado(ChamadoNovoReqVO novo, String solicitante, String origem);
    }

    public interface Links {
        /** filtro: problema | links | servicos | todos */
        ListaVO<LinkVO> listarLinks(Long unidadeId, String filtro, int limite);
    }

    /**
     * Estado real do link principal de cada unidade, para o telão (o monitoramento implementa). Sem implementação, o
     * telão usa o link simulado.
     */
    public interface IndicadoresLink {
        /** unidadeId → link principal; unidade sem monitor principal aparece no ar, sem latência (não é verificada) */
        Map<Long, LinkUnidade> linksPrincipais();
    }

    /**
     * @param link           online | degradado | offline
     * @param latenciaMs     última latência (null fora do ar)
     * @param disponibilidade disponibilidade das últimas 24 h, em %
     */
    public record LinkUnidade(String link, Integer latenciaMs, Double disponibilidade) {
    }

    public interface Inventario {
        /** filtro: todos | Desktop | Notebook | Servidor | eol | av | old */
        ListaVO<EquipamentoVO> listarEquipamentos(Long unidadeId, String filtro, String busca, int limite);
    }

    public interface Logs {
        /** nivel: todos | info | aviso | erro | critico */
        ListaVO<LogVO> buscarLogs(Long unidadeId, String nivel, String busca, int limite);
    }

    public interface Cameras {
        CameraResumoVO resumoCameras(Long unidadeId, int limiteEventos);
    }

}
