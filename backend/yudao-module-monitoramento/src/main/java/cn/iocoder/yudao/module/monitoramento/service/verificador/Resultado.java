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
 */
public record Resultado(boolean ok, Integer latenciaMs, String msg, Integer certDias, LocalDateTime certValidade) {

    public static Resultado ok(Integer latenciaMs, String msg) {
        return new Resultado(true, latenciaMs, msg, null, null);
    }

    public static Resultado falha(String msg) {
        return new Resultado(false, null, msg, null, null);
    }

    public static Resultado falha(Integer latenciaMs, String msg) {
        return new Resultado(false, latenciaMs, msg, null, null);
    }

    public Resultado comCertificado(Integer dias, LocalDateTime validade) {
        return new Resultado(ok, latenciaMs, msg, dias, validade);
    }

}
