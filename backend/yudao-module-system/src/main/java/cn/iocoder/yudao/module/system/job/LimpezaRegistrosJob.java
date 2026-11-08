package cn.iocoder.yudao.module.system.job;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import cn.iocoder.yudao.module.system.service.retencao.RetencaoService;
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

import static cn.iocoder.yudao.module.system.service.retencao.RetencaoService.*;

/**
 * Retenção dos registros técnicos que só crescem: tokens de sessão expirados; logs de operação, acesso, API e tarefas; avisos
 * do sino; e-mails já enviados; e o controle dos avisos de SLA. Os prazos vêm da tela de Infraestrutura ({@link RetencaoService}).
 * Roda todo dia pelo agendador do Spring (as "Tarefas agendadas" do RuoYi dependem do Quartz, que o CO-TI não liga).
 *
 * <p>Apaga em lotes, para não segurar a tabela travada nem gerar uma transação gigante. Prazo 0 = guardar para sempre.
 * Dados do negócio (chamados, conversas, chat, inventário, trilha de auditoria) não entram aqui. O registro de e-mails recebidos
 * também não: ele evita que um e-mail ainda na caixa vire chamado de novo.
 */
@Component
@Slf4j
public class LimpezaRegistrosJob {

    private static final int LOTE = 5000;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Resource
    private RetencaoService retencao;

    @Value("${coti.retencao.log-api-dias:30}")
    private int logApiDias;
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
        apagar(apagados, "system_operate_log", "create_time", limite(agora, retencao.valor(LOG_OPERACOES)));
        apagar(apagados, "system_login_log", "create_time", limite(agora, retencao.valor(LOG_ACESSO)));
        apagar(apagados, "infra_api_access_log", "create_time", limite(agora, logApiDias));
        apagar(apagados, "infra_api_error_log", "create_time", limite(agora, retencao.valor(LOG_ERROS)));
        apagar(apagados, "infra_job_log", "create_time", limite(agora, logTarefasDias));
        apagar(apagados, "system_notify_message", "create_time", limite(agora, retencao.valor(SINO_LIDAS)), " AND read_status = true");
        apagar(apagados, "system_notify_message", "create_time", limite(agora, retencao.valor(SINO_NAO_LIDAS)), "");
        apagar(apagados, "itsm.email_saida", "criado_em", limite(agora, retencao.valor(EMAIL_ENVIADO)), " AND status = 'enviado'");
        apagar(apagados, "itsm.sla_aviso", "criado_em", limite(agora, retencao.valor(SLA_AVISO)), "");
        apagar(apagados, "integracao.entrega", "criado_em", limite(agora, retencao.valor(INTEGRACAO_EVENTOS)), " AND status IN ('enviado', 'erro')");
        apagar(apagados, "integracao.evento", "recebido_em", limite(agora, retencao.valor(INTEGRACAO_EVENTOS)),
                " AND status NOT IN ('recebido', 'processando', 'agendado', 'confirmacao')");
        apagados.values().removeIf(n -> n == 0);
        if (!apagados.isEmpty()) {
            log.info("[retenção] registros antigos apagados: {}", apagados);
        }
    }

    private static LocalDateTime limite(LocalDateTime agora, int dias) {
        return dias <= 0 ? null : agora.minusDays(dias);
    }

    private void apagar(Map<String, Integer> apagados, String tabela, String coluna, LocalDateTime antesDe) {
        apagar(apagados, tabela, coluna, antesDe, "");
    }

    /** Tabela, coluna e condição são constantes desta classe (nunca vêm de fora); só a data vai como parâmetro. */
    private void apagar(Map<String, Integer> apagados, String tabela, String coluna, LocalDateTime antesDe, String condicao) {
        if (antesDe == null) {
            return;
        }
        // ctid: nem toda tabela tem a coluna id (itsm.sla_aviso)
        String sql = "DELETE FROM " + tabela + " WHERE ctid = ANY(ARRAY(SELECT ctid FROM " + tabela + " WHERE " + coluna + " < ?"
                + condicao + " LIMIT " + LOTE + "))";
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
        apagados.merge(tabela, total, Integer::sum);
    }

}
