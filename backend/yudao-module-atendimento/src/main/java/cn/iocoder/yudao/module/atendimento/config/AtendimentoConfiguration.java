package cn.iocoder.yudao.module.atendimento.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AtendimentoProperties.class)
public class AtendimentoConfiguration {
}
