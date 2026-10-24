package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.itsm.dominio.Calendario;
import cn.iocoder.yudao.module.itsm.service.SlaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Horário de atendimento da caixa, pelo calendário do Service Desk (expediente e feriados, os mesmos do SLA).
 */
@Service
public class HorarioService {

    @Resource
    private SlaService slaService;

    /** Caixa sem calendário (ou com calendário excluído) está sempre em horário. */
    public boolean emExpediente(CaixaDO caixa) {
        if (caixa.getCalendarioId() == null) {
            return true;
        }
        Calendario cal = slaService.calendario(caixa.getCalendarioId());
        return cal == null || cal.emExpediente(LocalDateTime.now());
    }

}
