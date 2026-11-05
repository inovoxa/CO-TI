package cn.iocoder.yudao.module.infraestrutura.service;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** De hora em hora: atualiza a medida do dia e confere o espaço. */
@Slf4j
@Component
public class CapacidadeJob {

    @Resource
    private CapacidadeService capacidadeService;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Scheduled(cron = "${coti.infra.capacidade-cron:0 13 * * * *}")
    public void medir() {
        execucaoUnica.executar("infra:capacidade", Duration.ofMinutes(50), () -> {
            try {
                capacidadeService.registrarHoje();
                capacidadeService.conferirDisco();
            } catch (RuntimeException e) {
                log.error("[medir][falha ao medir a capacidade]", e);
            }
        });
    }

}
