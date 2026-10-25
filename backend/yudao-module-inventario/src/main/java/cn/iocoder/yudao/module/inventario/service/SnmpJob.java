package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredura SNMP no intervalo configurado ({@code coti.inventario.snmp-intervalo-ms}).
 */
@Component
@Slf4j
public class SnmpJob {

    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private SnmpService snmpService;

    private long ultima = System.currentTimeMillis();

    @Scheduled(fixedDelay = 60_000, initialDelay = 180_000)
    public void executar() {
        long intervalo = inventarioProperties.getSnmpIntervaloMs();
        if (intervalo <= 0 || System.currentTimeMillis() - ultima < intervalo) {
            return;
        }
        ultima = System.currentTimeMillis();
        try {
            snmpService.varrer();
        } catch (ServiceException e) {
            log.info("[snmp] varredura agendada não rodou: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("[snmp] falha na varredura agendada", e);
        }
    }

}
