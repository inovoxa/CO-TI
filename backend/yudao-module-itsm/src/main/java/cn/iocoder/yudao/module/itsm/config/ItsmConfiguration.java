package cn.iocoder.yudao.module.itsm.config;

import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ItsmProperties.class)
public class ItsmConfiguration {

    /**
     * Escopo de dados por unidade: o perfil "só o próprio departamento" (e afins) vê só chamados, problemas, mudanças, contratos, licenças, orçamentos e projetos daquelas unidades.
     *
     * <p>A regra do yudao compara o nome da tabela como o JSqlParser o devolve, SEM o schema ("chamado"), por isso
     * registramos pelo nome simples; com "itsm.chamado" o filtro nunca seria aplicado.
     */
    @Bean
    public DeptDataPermissionRuleCustomizer itsmDeptDataPermissionRuleCustomizer() {
        return rule -> {
            rule.addDeptColumn("chamado", "dept_id");
            rule.addUserColumn("chamado", "registrado_por");
            rule.addDeptColumn("problema", "dept_id");
            rule.addUserColumn("problema", "registrado_por");
            rule.addDeptColumn("mudanca", "dept_id");
            rule.addUserColumn("mudanca", "registrado_por");
            // governança: o "próprio" de um contrato, licença ou projeto é quem responde por ele
            for (String tabela : new String[]{"contrato", "licenca", "projeto"}) {
                rule.addDeptColumn(tabela, "dept_id");
                rule.addUserColumn(tabela, "responsavel_id");
            }
            rule.addDeptColumn("orcamento", "dept_id");
        };
    }

}
