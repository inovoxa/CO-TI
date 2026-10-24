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

    /** API Graph da Meta (WhatsApp Cloud API), com a versão. Trocado só em teste (simulador). */
    private String metaGraphUrl = "https://graph.facebook.com/v23.0";

    /** API do Instagram (Instagram API com login do Instagram), com a versão. */
    private String instagramUrl = "https://graph.instagram.com/v23.0";

    /** Bot API do Telegram. */
    private String telegramUrl = "https://api.telegram.org";

    /** Consulta de CEP do fluxo do catálogo (ViaCEP: {url}/{cep}/json/). Vazio = não consulta. */
    private String viacepUrl = "https://viacep.com.br/ws";

    /** Tamanho máximo de mídia baixada dos canais (bytes). */
    private long midiaMaxima = 25L * 1024 * 1024;

}
