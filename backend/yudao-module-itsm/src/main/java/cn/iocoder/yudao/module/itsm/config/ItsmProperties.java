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

    /**
     * Dias até fechar sozinho um chamado solucionado sem resposta do solicitante (GLPI: autoclose_delay).
     * 0 = fecha na próxima verificação; negativo = nunca.
     */
    private int fechamentoAutomaticoDias = 7;

    /** Envia a pesquisa de satisfação ao fechar. */
    private boolean pesquisaSatisfacao = true;

}
