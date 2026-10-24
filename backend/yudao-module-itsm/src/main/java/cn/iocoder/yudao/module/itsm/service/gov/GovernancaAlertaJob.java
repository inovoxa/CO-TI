package cn.iocoder.yudao.module.itsm.service.gov;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Avisos diários de contratos perto do prazo e licenças perto de expirar (GLPI: Contract::cronContract e
 * SoftwareLicense::cronSoftware). Cada prazo gera um aviso só, mesmo com várias instâncias rodando: a referência
 * gravada no contrato ou na licença impede repetir.
 */
@Slf4j
@Component
public class GovernancaAlertaJob {

    @Resource
    private ContratoService contratoService;
    @Resource
    private LicencaService licencaService;

    @Scheduled(cron = "${coti.itsm.governanca.alerta-cron:0 0 7 * * *}")
    public void executar() {
        try {
            int c = contratoService.enviarAlertas(LocalDate.now());
            int l = licencaService.enviarAlertas(LocalDate.now());
            if (c + l > 0) {
                log.info("[executar][avisos: {} contrato(s), {} licença(s)]", c, l);
            }
        } catch (Exception e) {
            log.error("[executar][falha nos avisos de governança]", e);
        }
    }

}
