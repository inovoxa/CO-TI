package cn.iocoder.yudao.module.logs.service;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import cn.iocoder.yudao.module.system.service.retencao.AvisoInfraService;
import cn.iocoder.yudao.module.system.service.retencao.RetencaoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Retenção: apaga os pedaços diários de logs mais antigos que o prazo da tela de Infraestrutura (drop_chunks, sem varrer linha
 * a linha). A compressão dos pedaços com mais de 2 dias é feita pelo próprio TimescaleDB (V45).
 *
 * <p>Uma fonte barulhenta pode encher o disco muito antes do prazo. Por isso, a cada hora, o tamanho total é conferido: acima do
 * limite, saem os dias mais antigos, sempre preservando {@value #DIAS_PRESERVADOS} dias, e os administradores são avisados.
 */
@Slf4j
@Component
public class RetencaoLogsJob {

    static final int DIAS_PRESERVADOS = 7;
    private static final long GB = 1024L * 1024 * 1024;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private RetencaoService retencao;
    @Resource
    private AvisoInfraService avisoInfra;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Scheduled(cron = "${coti.logs.retencao-cron:0 47 3 * * *}")
    public void limpar() {
        execucaoUnica.executar("logs:retencao", Duration.ofHours(1), this::limparAgora);
    }

    @Scheduled(cron = "${coti.logs.limite-cron:0 23 * * * *}")
    public void limitarTamanho() {
        execucaoUnica.executar("logs:limite", Duration.ofMinutes(50), this::limitarTamanhoAgora);
    }

    private void limitarTamanhoAgora() {
        int limiteGb = retencao.valor(RetencaoService.LOGS_LIMITE_GB);
        if (limiteGb <= 0 || tamanho() <= limiteGb * GB) {
            return;
        }
        List<Timestamp> fins = jdbcTemplate.queryForList("SELECT range_end::timestamp FROM timescaledb_information.chunks "
                + "WHERE hypertable_schema = 'logs' AND hypertable_name = 'mensagem' AND range_end::timestamp <= ? ORDER BY range_end",
                Timestamp.class, Timestamp.valueOf(LocalDateTime.now().minusDays(DIAS_PRESERVADOS)));
        int dias = 0;
        for (Timestamp fim : fins) {
            jdbcTemplate.queryForObject("SELECT public.coti_apagar_pedacos('logs.mensagem', ?)", Integer.class, fim);
            dias++;
            if (tamanho() <= limiteGb * GB) {
                break;
            }
        }
        boolean aindaAcima = tamanho() > limiteGb * GB;
        log.warn("[logs] acima de {} GB: {} dias mais antigos apagados{}", limiteGb, dias, aindaAcima ? "; continua acima do limite" : "");
        avisoInfra.avisar("logs-limite", aindaAcima
                ? "Os logs passaram de " + limiteGb + " GB mesmo guardando só os últimos " + DIAS_PRESERVADOS
                + " dias. Reduza o volume das fontes ou aumente o limite."
                : "Os logs passaram de " + limiteGb + " GB e " + dias + " dia(s) mais antigos foram apagados antes do prazo de retenção.");
    }

    private long tamanho() {
        Long n = jdbcTemplate.queryForObject("SELECT hypertable_size('logs.mensagem')", Long.class);
        return n == null ? 0 : n;
    }

    private void limparAgora() {
        try {
            LocalDateTime limite = LocalDateTime.now().minusDays(Math.max(1, retencao.valor(RetencaoService.LOGS_MENSAGENS)));
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
