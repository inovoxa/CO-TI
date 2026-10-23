package cn.iocoder.yudao.module.identidade.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdProperties.class)
public class IdentidadeConfiguration {
}
