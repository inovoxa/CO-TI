package cn.iocoder.yudao.module.logs.dominio;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Uma mensagem de log já interpretada, pronta para gravar.
 *
 * @param fluxo     firewall | contas | maquinas | arquivos | geral
 * @param origem    quem gerou (devname do FortiGate, computador do Windows, host do Syslog)
 * @param ip        endereço de onde chegou
 * @param nivel     severidade do Syslog (0 emergência ... 7 depuração)
 * @param aplicacao programa (tag do Syslog, canal do Windows)
 * @param campos    campos separados (valores em texto)
 */
public record Mensagem(LocalDateTime momento, LocalDateTime recebido, String fluxo, String origem, String ip, int nivel,
                       String aplicacao, String mensagem, Map<String, String> campos) {
}
