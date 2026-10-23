package cn.iocoder.yudao.module.system.job.monitor;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.system.service.monitor.MonitorKpiService;
import jakarta.annotation.Resource; // no branch master (JDK 8) troque por javax.annotation.Resource
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A cada 5 segundos atualiza os indicadores de TI e empurra para todos os usuários
 * do painel administrativo conectados no WebSocket (/infra/ws).
 *
 * <p>Mensagens enviadas:
 * <ul>
 *   <li>{"type":"monitor-kpi","content":"[ {...UnitKpiVO...}, ... ]"} — toda rodada;</li>
 *   <li>{"type":"monitor-ocorrencia","content":"[ {...OcorrenciaVO...}, ... ]"} — só quando algo muda.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableScheduling
public class MonitorPushJob {

    public static final String TIPO_KPI = "monitor-kpi";
    public static final String TIPO_OCORRENCIA = "monitor-ocorrencia";

    @Resource
    private MonitorKpiService monitorKpiService;
    @Resource
    private WebSocketMessageSender webSocketMessageSender;
    @Resource
    private cn.iocoder.yudao.module.system.service.monitor.MatrixNotifier matrixNotifier;

    @Scheduled(fixedRate = 5000, initialDelay = 10000)
    public void push() {
        try {
            MonitorKpiService.Rodada rodada = monitorKpiService.atualizarTodas();
            if (!rodada.leituras.isEmpty()) {
                webSocketMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(), TIPO_KPI, rodada.leituras);
            }
            if (!rodada.novasOcorrencias.isEmpty()) {
                webSocketMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(), TIPO_OCORRENCIA, rodada.novasOcorrencias);
                // também no Matrix (#noc-alertas), fora da thread do agendador para não atrasar o telão
                java.util.Map<Long, String> nomes = monitorKpiService.getUnidadesAtivas().stream()
                        .collect(java.util.stream.Collectors.toMap(u -> u.getId(), u -> u.getName(), (a, b) -> a));
                java.util.List<cn.iocoder.yudao.module.system.controller.admin.monitor.vo.OcorrenciaVO> copia =
                        new java.util.ArrayList<>(rodada.novasOcorrencias);
                java.util.concurrent.CompletableFuture.runAsync(() -> matrixNotifier.enviar(copia, nomes));
            }
        } catch (Exception e) {
            log.error("[push][falha ao enviar indicadores do telão]", e);
        }
    }

}
