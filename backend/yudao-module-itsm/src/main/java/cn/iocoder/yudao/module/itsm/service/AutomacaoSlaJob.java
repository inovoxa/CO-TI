package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.SlaNivelDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automação do SLA: escalonamento por nível (GLPI: SlaLevel_Ticket::cronSlaTicket) e fechamento automático de
 * chamados solucionados (GLPI: Ticket::cronCloseTicket). Seguro com mais de uma instância: cada execução trava
 * a linha do chamado e o registro de nível executado tem índice único.
 */
@Slf4j
@Component
public class AutomacaoSlaJob {

    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private SlaService slaService;
    @Resource
    private ItsmProperties props;

    @Scheduled(fixedDelayString = "${coti.itsm.escalonamento-intervalo-ms:60000}", initialDelay = 45_000)
    public void escalonar() {
        try {
            int n = escalonarAgora(LocalDateTime.now());
            if (n > 0) {
                log.info("[escalonar][{} níveis de escalonamento executados]", n);
            }
        } catch (Exception e) {
            log.error("[escalonar][falha no escalonamento de SLA]", e);
        }
    }

    @Scheduled(fixedDelayString = "${coti.itsm.fechamento-intervalo-ms:3600000}", initialDelay = 90_000)
    public void fecharSolucionados() {
        try {
            int n = fecharAgora(LocalDateTime.now());
            if (n > 0) {
                log.info("[fecharSolucionados][{} chamados fechados automaticamente]", n);
            }
        } catch (Exception e) {
            log.error("[fecharSolucionados][falha no fechamento automático]", e);
        }
    }

    /** @return quantos níveis foram executados */
    @DataPermission(enable = false)
    public int escalonarAgora(LocalDateTime agora) {
        List<ChamadoDO> abertos = chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>()
                .in(ChamadoDO::getStatus, ChamadoStatus.NOVO.getCodigo(), ChamadoStatus.ATRIBUIDO.getCodigo(),
                        ChamadoStatus.PLANEJADO.getCodigo())
                .and(w -> w.isNotNull(ChamadoDO::getSlaSolucaoId).or().isNotNull(ChamadoDO::getSlaAtendimentoId)));
        Map<Long, List<SlaNivelDO>> cache = new HashMap<>();
        int executados = 0;
        for (ChamadoDO c : abertos) {
            // atendimento: só enquanto ninguém assumiu
            if (c.getSlaAtendimentoId() != null && c.getAssumidoEm() == null && c.getPrazoAtendimento() != null) {
                executados += executarDevidos(c, c.getSlaAtendimentoId(), c.getPrazoAtendimento(), "atendimento", agora, cache);
            }
            if (c.getSlaSolucaoId() != null && c.getPrazoSolucao() != null) {
                executados += executarDevidos(c, c.getSlaSolucaoId(), c.getPrazoSolucao(), "solução", agora, cache);
            }
        }
        return executados;
    }

    private int executarDevidos(ChamadoDO c, Long slaId, LocalDateTime prazo, String qual, LocalDateTime agora,
                                Map<Long, List<SlaNivelDO>> cache) {
        int n = 0;
        for (SlaNivelDO nivel : cache.computeIfAbsent(slaId, slaService::niveisAtivos)) {
            if (!agora.isBefore(prazo.plusSeconds(nivel.getTempoSeg()))) {
                try {
                    if (chamadoService.escalonar(c.getId(), nivel, qual)) {
                        n++;
                    }
                } catch (Exception e) {
                    log.error("[executarDevidos][chamado {} nível {}]", c.getId(), nivel.getId(), e);
                }
            }
        }
        return n;
    }

    /** @return quantos chamados foram fechados */
    @DataPermission(enable = false)
    public int fecharAgora(LocalDateTime agora) {
        if (props.getFechamentoAutomaticoDias() < 0) {
            return 0;
        }
        LocalDateTime limite = agora.minusDays(props.getFechamentoAutomaticoDias());
        int n = 0;
        for (ChamadoDO c : chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>()
                .eq(ChamadoDO::getStatus, ChamadoStatus.SOLUCIONADO.getCodigo())
                .le(ChamadoDO::getSolucionadoEm, limite))) {
            try {
                if (chamadoService.fecharAutomatico(c.getId())) {
                    n++;
                }
            } catch (Exception e) {
                log.error("[fecharAgora][chamado {}]", c.getId(), e);
            }
        }
        return n;
    }

}
