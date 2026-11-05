package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.system.service.retencao.RetencaoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Retenção: apaga os pedaços diários de batidas (drop_chunks do TimescaleDB, sem varrer linha a linha) e os totais por hora
 * mais antigos que o prazo definido na tela de Infraestrutura. A compressão é feita pelo próprio TimescaleDB (V73).
 */
@Component
@Slf4j
public class LimpezaBatidasJob {

    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private RetencaoService retencao;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Scheduled(cron = "${coti.monitoramento.retencao-cron:0 17 3 * * *}")
    public void limpar() {
        execucaoUnica.executar("monitoramento:retencao", Duration.ofHours(1), this::limparAgora);
    }

    private void limparAgora() {
        try {
            LocalDateTime agora = LocalDateTime.now();
            int pedacos = monMonitorMapper.apagarBatidasAntigas(agora.minusDays(Math.max(1, retencao.valor(RetencaoService.MONITOR_BATIDAS))));
            int horas = monMonitorMapper.apagarHorasAntigas(agora.minusDays(Math.max(30, retencao.valor(RetencaoService.MONITOR_HORAS))));
            if (pedacos + horas > 0) {
                log.info("[monitoramento] retenção: {} dias de batidas e {} totais por hora apagados", pedacos, horas);
            }
        } catch (RuntimeException e) {
            log.error("[monitoramento] falha na retenção das batidas", e);
        }
    }

}
