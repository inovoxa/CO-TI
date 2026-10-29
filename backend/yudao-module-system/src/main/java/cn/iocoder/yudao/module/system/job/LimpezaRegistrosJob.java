package cn.iocoder.yudao.module.system.job;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Retenção dos registros técnicos que só crescem: tokens de sessão expirados e logs de operação, acesso, API e tarefas.
 * Roda todo dia pelo agendador do Spring (as "Tarefas agendadas" do RuoYi dependem do Quartz, que o CO-TI não liga).
 *
 * <p>Apaga em lotes, para não segurar a tabela travada nem gerar uma transação gigante. Prazo 0 = guardar para sempre.
 * Dados do negócio (chamados, conversas, inventário, trilha de auditoria) não entram aqui.
 */
@Component
@Slf4j
public class LimpezaRegistrosJob {

    private static final int LOTE = 5000;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Value("${coti.retencao.log-operacoes-dias:730}")
    private int logOperacoesDias;
    @Value("${coti.retencao.log-acesso-dias:365}")
    private int logAcessoDias;
    @Value("${coti.retencao.log-api-dias:30}")
    private int logApiDias;
    @Value("${coti.retencao.log-erros-dias:90}")
    private int logErrosDias;
    @Value("${coti.retencao.log-tarefas-dias:30}")
    private int logTarefasDias;

    @Scheduled(cron = "${coti.retencao.cron:0 37 3 * * *}")
    public void limpar() {
        execucaoUnica.executar("system:retencao", Duration.ofHours(1), this::limparAgora);
    }

    private void limparAgora() {
        LocalDateTime agora = LocalDateTime.now();
        Map<String, Integer> apagados = new LinkedHashMap<>();
        // tokens: um dia de folga depois de expirar, para não cortar uma renovação em andamento
        apagar(apagados, "system_oauth2_access_token", "expires_time", agora.minusDays(1));
        apagar(apagados, "system_oauth2_refresh_token", "expires_time", agora.minusDays(1));
        apagar(apagados, "system_operate_log", "create_time", limite(agora, logOperacoesDias));
        apagar(apagados, "system_login_log", "create_time", limite(agora, logAcessoDias));
        apagar(apagados, "infra_api_access_log", "create_time", limite(agora, logApiDias));
        apagar(apagados, "infra_api_error_log", "create_time", limite(agora, logErrosDias));
        apagar(apagados, "infra_job_log", "create_time", limite(agora, logTarefasDias));
        apagados.values().removeIf(n -> n == 0);
        if (!apagados.isEmpty()) {
            log.info("[retenção] registros antigos apagados: {}", apagados);
        }
    }

    private static LocalDateTime limite(LocalDateTime agora, int dias) {
        return dias <= 0 ? null : agora.minusDays(dias);
    }

    /** Tabela e coluna são constantes desta classe (nunca vêm de fora); só a data vai como parâmetro. */
    private void apagar(Map<String, Integer> apagados, String tabela, String coluna, LocalDateTime antesDe) {
        if (antesDe == null) {
            return;
        }
        String sql = "DELETE FROM " + tabela + " WHERE id IN (SELECT id FROM " + tabela + " WHERE " + coluna + " < ? LIMIT " + LOTE + ")";
        int total = 0;
        try {
            int n;
            do {
                n = jdbcTemplate.update(sql, Timestamp.valueOf(antesDe));
                total += n;
            } while (n == LOTE);
        } catch (RuntimeException e) {
            log.error("[retenção] falha ao limpar {}", tabela, e);
        }
        apagados.put(tabela, total);
    }

}
