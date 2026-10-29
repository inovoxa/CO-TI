package cn.iocoder.yudao.module.logs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Logs centralizados ({@code coti.logs.*}).
 */
@ConfigurationProperties(prefix = "coti.logs")
@Data
public class LogsProperties {

    /** Liga a recepção (as portas abaixo). A consulta e os painéis funcionam mesmo desligada. */
    private boolean recepcao = true;

    /** Portas dentro do contêiner (acima de 1024: o processo não roda como root). 0 = não abre. */
    private int syslogUdpPorta = 1514;
    private int syslogTcpPorta = 1514;
    private int gelfUdpPorta = 12201;

    /** Portas publicadas no servidor (docker-compose), só para a tela de fontes mostrar onde apontar. */
    private int portaPublicaSyslog = 514;
    private int portaPublicaGelf = 12201;

    /**
     * Quem pode mandar logs: endereços ou redes (ex.: 172.16.0.0/12, 10.0.0.5). Vazio = qualquer um.
     * Atrás do Docker Desktop o endereço visto pode ser o do próprio Docker; aí deixe vazio e feche no firewall.
     */
    private List<String> permitidos = new ArrayList<>();

    /** Mensagens aguardando gravação; acima disso as novas são descartadas (e contadas). */
    private int filaMaxima = 200_000;

    /** Threads gravando no banco em paralelo (cada uma interpreta e grava seus lotes). */
    private int gravadores = 3;

    /** Linhas por gravação no banco (COPY). */
    private int lote = 5_000;

    /** Dias de logs guardados; os pedaços diários mais antigos são apagados de madrugada. */
    private int retencaoDias = 30;

    /** Tamanho máximo de uma mensagem guardada (o resto é cortado). */
    private int mensagemMaxima = 16_384;

}
