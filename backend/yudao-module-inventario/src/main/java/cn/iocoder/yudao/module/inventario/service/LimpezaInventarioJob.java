package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * De hora em hora, em uma instância só: apaga os envios dos agentes que passaram do prazo e dá baixa automática nas
 * máquinas paradas (quando ligada).
 */
@Component
@Slf4j
public class LimpezaInventarioJob {

    @Resource
    private EnvioAgenteService envioAgenteService;
    @Resource
    private LimpezaInventarioService limpezaInventarioService;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    public void executar() {
        execucaoUnica.executar("inventario:limpeza", Duration.ofMinutes(55), this::limpar);
    }

    private void limpar() {
        try {
            int envios = envioAgenteService.apagarAntigos();
            int baixados = limpezaInventarioService.baixaAutomatica();
            if (envios + baixados > 0) {
                log.info("[inventario] limpeza: {} envios antigos apagados, {} máquinas paradas baixadas", envios, baixados);
            }
        } catch (RuntimeException e) {
            log.error("[inventario] falha na limpeza", e);
        }
    }

}
