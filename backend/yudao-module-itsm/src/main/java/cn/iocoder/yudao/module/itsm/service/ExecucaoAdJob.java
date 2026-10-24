package cn.iocoder.yudao.module.itsm.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda as operações no AD liberadas (substitui o gatilho do WF14 do n8n). Seguro com mais de uma instância:
 * cada execução é reivindicada com um UPDATE condicional.
 */
@Slf4j
@Component
public class ExecucaoAdJob {

    @Resource
    private ExecucaoAdService execucaoAdService;

    @Scheduled(fixedDelayString = "${coti.itsm.ad.intervalo-ms:15000}", initialDelay = 30_000)
    public void executar() {
        try {
            int n = execucaoAdService.processarFila();
            if (n > 0) {
                log.info("[executar][{} operações no AD processadas]", n);
            }
        } catch (Exception e) {
            log.error("[executar][falha ao processar a fila do AD]", e);
        }
    }

}
