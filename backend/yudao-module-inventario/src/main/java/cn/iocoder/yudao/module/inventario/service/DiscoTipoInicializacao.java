package cn.iocoder.yudao.module.inventario.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Na subida do backend, classifica o tipo de disco (SSD/HD) dos ativos inventariados antes da classificação existir.
 * Só toca ativos com discos e sem tipo: depois da primeira vez, não há o que fazer.
 */
@Component
@Slf4j
public class DiscoTipoInicializacao {

    @Resource
    private AtivoService ativoService;

    @EventListener(ApplicationReadyEvent.class)
    public void classificar() {
        try {
            int n = ativoService.classificarDiscosPendentes();
            if (n > 0) {
                log.info("[inventario] tipo de disco classificado em {} ativos", n);
            }
        } catch (RuntimeException e) {
            log.error("[inventario] falha ao classificar o tipo de disco", e);
        }
    }

}
