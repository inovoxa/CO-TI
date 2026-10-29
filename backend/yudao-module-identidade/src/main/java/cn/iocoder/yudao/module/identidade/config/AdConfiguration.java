package cn.iocoder.yudao.module.identidade.config;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import cn.iocoder.yudao.module.identidade.ad.AdDiretorio;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import cn.iocoder.yudao.module.identidade.service.AdAutenticador;
import cn.iocoder.yudao.module.identidade.service.AdProvisionamentoService;
import cn.iocoder.yudao.module.identidade.service.AdSincronizacaoService;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/**
 * Beans do AD: só existem com {@code coti.ad.habilitado=true}. Sem eles o login usa só contas locais.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "coti.ad", name = "habilitado", havingValue = "true")
public class AdConfiguration {

    /** Lazy: se o AD estiver fora do ar na partida, o portal sobe mesmo assim (o admin local continua entrando). */
    @Bean(destroyMethod = "close")
    @Lazy
    public AdDiretorio adDiretorio(AdProperties props) {
        return new AdDiretorio(props);
    }

    @Bean
    public AdAutenticador adAutenticador(@Lazy AdDiretorio diretorio, AdProvisionamentoService provisionamento,
                                         AdminUserMapper adminUserMapper, VinculoAdMapper vinculoAdMapper) {
        return new AdAutenticador(diretorio, provisionamento, adminUserMapper, vinculoAdMapper);
    }

    @Bean
    public AdSincronizacaoService adSincronizacaoService(@Lazy AdDiretorio diretorio,
                                                         AdProvisionamentoService provisionamento,
                                                         VinculoAdMapper vinculoAdMapper, AdProperties props,
                                                         AuditoriaService auditoria) {
        return new AdSincronizacaoService(diretorio, provisionamento, vinculoAdMapper, props, auditoria);
    }

    @Bean
    public AdSincronizacaoJob adSincronizacaoJob(AdSincronizacaoService servico, ExecucaoUnica execucaoUnica) {
        return new AdSincronizacaoJob(servico, execucaoUnica);
    }

    /** Com mais de um backend, cada vez da sincronização roda em uma instância só (a primeira que chega). */
    public static class AdSincronizacaoJob {

        private final AdSincronizacaoService servico;
        private final ExecucaoUnica execucaoUnica;

        AdSincronizacaoJob(AdSincronizacaoService servico, ExecucaoUnica execucaoUnica) {
            this.servico = servico;
            this.execucaoUnica = execucaoUnica;
        }

        @Scheduled(cron = "${coti.ad.cron-sincronizacao:0 */30 * * * *}")
        public void executar() {
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
