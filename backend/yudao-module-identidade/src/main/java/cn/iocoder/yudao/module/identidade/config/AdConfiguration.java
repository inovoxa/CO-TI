package cn.iocoder.yudao.module.identidade.config;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import cn.iocoder.yudao.module.identidade.service.AdAutenticador;
import cn.iocoder.yudao.module.identidade.service.AdConfiguracaoService;
import cn.iocoder.yudao.module.identidade.service.AdProvisionamentoService;
import cn.iocoder.yudao.module.identidade.service.AdSincronizacaoService;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/**
 * Beans do AD. Existem sempre: quem decide se o AD está ligado é a configuração em vigor ({@link AdConfiguracaoService}), que
 * pode ser mudada na tela sem reiniciar. Desligado, o login usa só contas locais e a sincronização não roda.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class AdConfiguration {

    @Bean
    public AdAutenticador adAutenticador(AdConfiguracaoService configuracao, AdProvisionamentoService provisionamento,
                                         AdminUserMapper adminUserMapper, VinculoAdMapper vinculoAdMapper) {
        return new AdAutenticador(configuracao, provisionamento, adminUserMapper, vinculoAdMapper);
    }

    @Bean
    public AdSincronizacaoService adSincronizacaoService(AdConfiguracaoService configuracao,
                                                         AdProvisionamentoService provisionamento,
                                                         VinculoAdMapper vinculoAdMapper, AuditoriaService auditoria) {
        return new AdSincronizacaoService(configuracao, provisionamento, vinculoAdMapper, auditoria);
    }

    @Bean
    public AdSincronizacaoJob adSincronizacaoJob(AdSincronizacaoService servico, AdConfiguracaoService configuracao,
                                                 ExecucaoUnica execucaoUnica) {
        return new AdSincronizacaoJob(servico, configuracao, execucaoUnica);
    }

    /** Com mais de um backend, cada vez da sincronização roda em uma instância só (a primeira que chega). */
    public static class AdSincronizacaoJob {

        private final AdSincronizacaoService servico;
        private final AdConfiguracaoService configuracao;
        private final ExecucaoUnica execucaoUnica;

        AdSincronizacaoJob(AdSincronizacaoService servico, AdConfiguracaoService configuracao, ExecucaoUnica execucaoUnica) {
            this.servico = servico;
            this.configuracao = configuracao;
            this.execucaoUnica = execucaoUnica;
        }

        @Scheduled(cron = "${coti.ad.cron-sincronizacao:0 */30 * * * *}")
        public void executar() {
            if (!configuracao.atual().isHabilitado()) {
                return;
            }
            execucaoUnica.executar("identidade:sincronizacao-ad", Duration.ofMinutes(10), this::sincronizar);
        }

        private void sincronizar() {
            try {
                servico.sincronizarTudo();
            } catch (Exception e) {
                log.error("[AdSincronizacaoJob][falha na sincronização com o AD]", e);
            }
        }

    }

}
