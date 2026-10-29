package cn.iocoder.yudao.module.system.service.monitor.fonte;

import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.*;

import java.util.Map;

/**
 * Contratos das "fontes" de cada módulo. As telas e o controller só conhecem estas interfaces.
 *
 * <p>Chamados, links e inventário vêm dos módulos do CO-TI (classes {@code @Primary} em itsm, monitoramento e
 * inventario). Logs e câmeras ainda são atendidos por {@link FontesSimuladas}, e por isso as telas deles ficam
 * desativadas no menu. Para ligar uma ferramenta real, crie uma classe que implemente só a interface daquele módulo
 * e marque-a com {@code @Primary}. Exemplos: {@code GraylogLogs} (API de busca), {@code FrigateCameras} (eventos via
 * MQTT e API HTTP).
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

    /** Link principal e serviços de cada unidade, para o telão (o monitoramento implementa). */
    public interface IndicadoresLink {
        /** unidadeId → link principal; unidade sem monitor principal aparece no ar, sem latência (não é verificada) */
        Map<Long, LinkUnidade> linksPrincipais();

        /** unidadeId → monitores ativos da unidade (no ar / total); unidade sem monitores não aparece */
        Map<Long, ServicosUnidade> servicosPorUnidade();
    }

    public record ServicosUnidade(int noAr, int total) {
    }

    /** Chamados de cada unidade, para o telão (o Service Desk implementa). */
    public interface IndicadoresChamados {
        /** unidadeId → chamados abertos e com SLA de solução vencido; unidade sem chamados não aparece */
        Map<Long, ChamadosUnidade> chamadosPorUnidade();
    }

    public record ChamadosUnidade(int abertos, int slaVencido) {
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
