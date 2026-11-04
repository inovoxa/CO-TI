package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.SlaAvisoMapper;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Avisa no sino os técnicos do chamado quando o prazo entra na janela de aviso. Só olha prazos que ainda não venceram, para
 * a primeira execução não despejar avisos de chamados antigos. Pendente fica de fora, porque o prazo está parado.
 */
@Slf4j
@Component
public class SlaAvisoJob {

    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private SlaAvisoMapper slaAvisoMapper;
    @Resource
    private SinoChamadoService sino;

    @Value("${coti.itsm.sla-aviso-minutos:60}")
    private long minutos;

    @Scheduled(fixedDelayString = "${coti.itsm.sla-aviso-intervalo-ms:300000}", initialDelay = 60_000)
    public void executar() {
        try {
            int n = avisarAgora(LocalDateTime.now());
            if (n > 0) {
                log.info("[executar][{} avisos de SLA perto de vencer]", n);
            }
        } catch (Exception e) {
            log.error("[executar][falha no aviso de SLA]", e);
        }
    }

    /** @return quantos prazos foram avisados */
    @DataPermission(enable = false)
    public int avisarAgora(LocalDateTime agora) {
        LocalDateTime limite = agora.plusMinutes(minutos);
        List<Integer> abertos = List.of(ChamadoStatus.NOVO.getCodigo(), ChamadoStatus.ATRIBUIDO.getCodigo(), ChamadoStatus.PLANEJADO.getCodigo());
        int n = 0;
        // ainda não assumido: o prazo que importa primeiro é o de assumir
        for (ChamadoDO c : chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>().in(ChamadoDO::getStatus, abertos)
                .isNull(ChamadoDO::getAssumidoEm).between(ChamadoDO::getPrazoAtendimento, agora, limite))) {
            if (slaAvisoMapper.registrar(c.getId(), "atendimento", c.getPrazoAtendimento()) == 1) {
                sino.avisarSla(c, "sla_atendimento", c.getPrazoAtendimento());
                n++;
            }
        }
        for (ChamadoDO c : chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>().in(ChamadoDO::getStatus, abertos)
                .between(ChamadoDO::getPrazoSolucao, agora, limite))) {
            if (slaAvisoMapper.registrar(c.getId(), "solucao", c.getPrazoSolucao()) == 1) {
                sino.avisarSla(c, "sla_solucao", c.getPrazoSolucao());
                n++;
            }
        }
        return n;
    }

}
