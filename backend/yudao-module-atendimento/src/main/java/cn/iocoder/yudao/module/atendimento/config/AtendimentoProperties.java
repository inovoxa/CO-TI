package cn.iocoder.yudao.module.atendimento.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do atendimento ({@code coti.atendimento.*}).
 */
@ConfigurationProperties(prefix = "coti.atendimento")
@Data
public class AtendimentoProperties {

    /**
     * Endereço público do CO-TI (ex.: https://ti.empresa.gov.br). Os anexos mandados aos canais e o endereço de
     * entrada dos webhooks são montados a partir dele.
     */
    private String urlPublica = "";

    /** Intervalo da fila de envio aos canais (ms). */
    private long envioIntervaloMs = 2000;

}
