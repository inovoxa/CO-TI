package cn.iocoder.yudao.module.monitoramento.service.verificador;

import java.time.LocalDateTime;

/**
 * Resultado de uma verificação.
 *
 * @param ok            respondeu como esperado (antes de inverter)
 * @param latenciaMs    tempo de resposta; null quando não respondeu
 * @param msg           o que aconteceu, em português
 * @param certDias      dias até o certificado TLS vencer (só HTTPS)
 * @param certValidade  quando o certificado vence
 * @param estadoExterno estado decidido fora do CO-TI (monitor do Uptime Kuma): é espelhado como veio, sem
 *                      tentativas nem inversão daqui; null nas verificações feitas pelo próprio CO-TI
 */
public record Resultado(boolean ok, Integer latenciaMs, String msg, Integer certDias, LocalDateTime certValidade, Integer estadoExterno) {

    public Resultado(boolean ok, Integer latenciaMs, String msg, Integer certDias, LocalDateTime certValidade) {
        this(ok, latenciaMs, msg, certDias, certValidade, null);
    }

    public static Resultado ok(Integer latenciaMs, String msg) {
        return new Resultado(true, latenciaMs, msg, null, null);
    }

    public static Resultado falha(String msg) {
        return new Resultado(false, null, msg, null, null);
    }

    public static Resultado falha(Integer latenciaMs, String msg) {
        return new Resultado(false, latenciaMs, msg, null, null);
    }

    /** Estado que outro sistema já decidiu (0 fora, 1 no ar, 2 pendente, 3 manutenção). */
    public static Resultado espelho(int estado, Integer latenciaMs, String msg) {
        return new Resultado(estado == 1, latenciaMs, msg, null, null, estado);
    }

    public Resultado comCertificado(Integer dias, LocalDateTime validade) {
        return new Resultado(ok, latenciaMs, msg, dias, validade, estadoExterno);
    }

}
