package cn.iocoder.yudao.module.monitoramento.config;

import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MonitoramentoProperties.class)
public class MonitoramentoConfiguration {

    /** Escopo de dados por unidade: o perfil restrito à própria unidade vê só os monitores dela (tabela sem schema). */
    @Bean
    public DeptDataPermissionRuleCustomizer monitoramentoDeptDataPermissionRuleCustomizer() {
        return rule -> rule.addDeptColumn("monitor", "dept_id");
    }

}
