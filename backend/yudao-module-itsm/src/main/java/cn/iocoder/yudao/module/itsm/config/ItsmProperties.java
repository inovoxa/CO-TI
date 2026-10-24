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

    /**
     * Pasta onde está montada uma cópia de {@code files/} do GLPI (os anexos). Vazia = a importação não copia
     * arquivos. Só caminhos dentro dela são lidos.
     */
    private String importacaoPastaArquivos = "";

    /** Execução de operações no AD pedidas pelos formulários. */
    private Ad ad = new Ad();

    @Data
    public static class Ad {
        /** desligado (fica aguardando um técnico) | simulado (demonstração/testes) | ssh */
        private String modo = "desligado";
        /** Tentativas em falha de infraestrutura (rede, SSH) antes de marcar erro */
        private int tentativas = 3;
        private Ssh ssh = new Ssh();
    }

    @Data
    public static class Ssh {
        private String host;
        private int porta = 22;
        private String usuario;
        /** Caminho do arquivo da chave privada (preferido) */
        private String chavePrivada;
        private String senha;
        /** Impressão digital da chave do servidor, ex.: SHA256:abc... (obrigatória) */
        private String impressaoDigital;
        private String script = "C:\\Scripts\\Executar_AD_Automatico.ps1";
        private int tempoLimiteSeg = 120;
    }

}
