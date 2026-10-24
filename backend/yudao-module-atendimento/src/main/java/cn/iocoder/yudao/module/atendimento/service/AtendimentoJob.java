package cn.iocoder.yudao.module.atendimento.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefas do atendimento: entregar a fila aos canais e reabrir as conversas adiadas cujo prazo chegou.
 */
@Slf4j
@Component
public class AtendimentoJob {

    @Resource
    private EnvioService envioService;
    @Resource
    private ConversaService conversaService;

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

}
