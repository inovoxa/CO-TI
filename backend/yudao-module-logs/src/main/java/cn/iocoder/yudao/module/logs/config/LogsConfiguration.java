package cn.iocoder.yudao.module.logs.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LogsProperties.class)
public class LogsConfiguration {
}
