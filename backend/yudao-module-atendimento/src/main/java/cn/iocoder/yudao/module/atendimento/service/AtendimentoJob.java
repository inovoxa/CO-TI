package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tarefas do atendimento: entregar a fila aos canais, processar os webhooks pendentes (novas tentativas) e reabrir
 * as conversas adiadas cujo prazo chegou. Envio, webhooks e reabertura reivindicam cada linha, então rodam em todas
 * as instâncias; a limpeza diária roda em uma só.
 */
@Slf4j
@Component
public class AtendimentoJob {

    @Resource
    private EnvioService envioService;
    @Resource
    private ConversaService conversaService;
    @Resource
    private WebhookEntradaService webhookEntradaService;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Scheduled(fixedDelayString = "${coti.atendimento.envio-intervalo-ms:2000}", initialDelay = 30_000)
    public void enviar() {
        try {
            envioService.processarFila();
        } catch (Exception e) {
            log.error("[enviar][falha na fila de envio]", e);
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void adiadas() {
        try {
            int n = conversaService.reabrirAdiadas();
            if (n > 0) {
                log.info("[adiadas][{} conversas reabertas]", n);
            }
        } catch (Exception e) {
            log.error("[adiadas][falha]", e);
        }
    }

    @Scheduled(fixedDelay = 3_000, initialDelay = 30_000)
    public void webhooks() {
        try {
            webhookEntradaService.agendar();
        } catch (Exception e) {
            log.error("[webhooks][falha]", e);
        }
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void limparWebhooks() {
        execucaoUnica.executar("atendimento:limpar-webhooks", Duration.ofHours(1), this::limparWebhooksAgora);
    }

    private void limparWebhooksAgora() {
        try {
            int n = webhookEntradaService.limpar();
            if (n > 0) {
                log.info("[limparWebhooks][{} webhooks antigos apagados]", n);
            }
        } catch (Exception e) {
            log.error("[limparWebhooks][falha]", e);
        }
    }

}
