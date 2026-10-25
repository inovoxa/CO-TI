package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AlertaVOs;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reavalia os alertas do inventário no intervalo configurado ({@code coti.inventario.alertas-intervalo-ms}).
 */
@Component
@Slf4j
public class AlertaInventarioJob {

    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private AlertaInventarioService alertaInventarioService;

    private long ultima;

    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void executar() {
        long intervalo = inventarioProperties.getAlertasIntervaloMs();
        if (intervalo <= 0 || System.currentTimeMillis() - ultima < intervalo) {
            return;
        }
        ultima = System.currentTimeMillis();
        try {
            AlertaVOs.Avaliacao r = alertaInventarioService.avaliar();
            if (r.getAbertos() + r.getEncerrados() + r.getChamados() > 0) {
                log.info("[inventario] alertas: {} abertos, {} encerrados, {} chamados", r.getAbertos(), r.getEncerrados(), r.getChamados());
            }
        } catch (RuntimeException e) {
            log.error("[inventario] falha na avaliação dos alertas", e);
        }
    }

}
