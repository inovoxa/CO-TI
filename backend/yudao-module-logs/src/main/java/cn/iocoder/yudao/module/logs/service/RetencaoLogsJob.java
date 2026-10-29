package cn.iocoder.yudao.module.logs.service;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import cn.iocoder.yudao.module.logs.config.LogsProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Retenção: apaga os pedaços diários de logs mais antigos que {@code coti.logs.retencao-dias} (drop_chunks, sem varrer
 * linha a linha). A compressão dos pedaços com mais de 2 dias é feita pelo próprio TimescaleDB (V45).
 */
@Slf4j
@Component
public class RetencaoLogsJob {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private LogsProperties props;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Scheduled(cron = "0 47 3 * * *")
    public void limpar() {
        execucaoUnica.executar("logs:retencao", Duration.ofHours(1), this::limparAgora);
    }

    private void limparAgora() {
        try {
            LocalDateTime limite = LocalDateTime.now().minusDays(Math.max(1, props.getRetencaoDias()));
            Integer n = jdbcTemplate.queryForObject("SELECT public.coti_apagar_pedacos('logs.mensagem', ?)", Integer.class,
                    Timestamp.valueOf(limite));
            if (n != null && n > 0) {
                log.info("[logs] retenção: {} dias de logs apagados (antes de {})", n, limite.toLocalDate());
            }
        } catch (RuntimeException e) {
            log.error("[logs] falha na retenção", e);
        }
    }

}
