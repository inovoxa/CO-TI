package cn.iocoder.yudao.module.itsm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuração do ITSM ({@code coti.itsm.*}).
 */
@ConfigurationProperties(prefix = "coti.itsm")
@Data
public class ItsmProperties {

    /**
     * Matriz urgência × impacto → prioridade, no formato do GLPI ({"urgência": {"impacto": prioridade}}).
     * Vazia = matriz padrão do GLPI.
     */
    private Map<String, Map<String, Integer>> matrizPrioridade = new LinkedHashMap<>();

}
