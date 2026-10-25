package cn.iocoder.yudao.module.inventario.config;

import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventarioProperties.class)
public class InventarioConfiguration {

    /**
     * Escopo de dados por unidade: o perfil restrito à própria unidade vê só os ativos dela. Registrado pelo nome da
     * tabela sem o schema (é como o JSqlParser devolve; ver ItsmConfiguration).
     */
    @Bean
    public DeptDataPermissionRuleCustomizer inventarioDeptDataPermissionRuleCustomizer() {
        return rule -> {
            rule.addDeptColumn("ativo", "dept_id");
            rule.addUserColumn("ativo", "usuario_id");
            rule.addDeptColumn("alerta_ativo", "dept_id");
        };
    }

}
