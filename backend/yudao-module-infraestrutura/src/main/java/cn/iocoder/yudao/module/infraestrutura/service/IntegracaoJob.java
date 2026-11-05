package cn.iocoder.yudao.module.infraestrutura.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Processa os avisos recebidos. Seguro com mais de uma instância: cada evento é reivindicado com um UPDATE condicional. */
@Slf4j
@Component
public class IntegracaoJob {

    @Resource
    private IntegracaoService integracaoService;
    @Resource
    private WebhookSaidaService webhookSaidaService;

    @Scheduled(fixedDelayString = "${coti.integracao.intervalo-ms:10000}", initialDelay = 30_000)
    public void processar() {
        try {
            int n = integracaoService.processarFila();
            if (n > 0) {
                log.info("[processar][{} avisos de integração tratados]", n);
            }
        } catch (RuntimeException e) {
            log.error("[processar][falha na fila de integrações]", e);
        }
    }

    @Scheduled(fixedDelayString = "${coti.integracao.saida-intervalo-ms:10000}", initialDelay = 35_000)
    public void enviar() {
        try {
            webhookSaidaService.processarFila();
        } catch (RuntimeException e) {
            log.error("[enviar][falha na fila de webhooks de saída]", e);
        }
    }

}
