package cn.iocoder.yudao.module.itsm.service.email;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Envia a fila de notificações e coleta as caixas de entrada (GLPI: QueuedNotification::cronQueuedNotification e
 * MailCollector::cronMailgate). Seguro com mais de uma instância: o envio reivindica cada mensagem e o coletor
 * não processa duas vezes o mesmo Message-ID.
 */
@Slf4j
@Component
public class EmailJob {

    @Resource
    private EnvioEmailService envio;
    @Resource
    private ColetorEmailService coletor;

    @Scheduled(fixedDelayString = "${coti.itsm.email.envio-intervalo-ms:20000}", initialDelay = 40_000)
    public void enviar() {
        try {
            int n = envio.processarFila();
            if (n > 0) {
                log.info("[enviar][{} e-mails enviados]", n);
            }
        } catch (Exception e) {
            log.error("[enviar][falha na fila de e-mails]", e);
        }
    }

    @Scheduled(fixedDelayString = "${coti.itsm.email.coleta-intervalo-ms:120000}", initialDelay = 60_000)
    public void coletar() {
        try {
            int n = coletor.coletarTodas();
            if (n > 0) {
                log.info("[coletar][{} e-mails recebidos]", n);
            }
        } catch (Exception e) {
            log.error("[coletar][falha na coleta de e-mails]", e);
        }
    }

}
