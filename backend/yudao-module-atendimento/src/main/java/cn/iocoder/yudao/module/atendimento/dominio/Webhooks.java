package cn.iocoder.yudao.module.atendimento.dominio;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Assinatura HMAC-SHA256 dos webhooks (entrada e saída) e conferência do endereço de saída.
 */
public final class Webhooks {

    private Webhooks() {
    }

    public static String hmacSha256(String segredo, byte[] corpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(corpo));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Compara a assinatura recebida ("sha256=..." ou só o hexadecimal) em tempo constante. */
    public static boolean assinaturaValida(String segredo, byte[] corpo, String recebida) {
        if (segredo == null || segredo.isEmpty() || recebida == null) {
            return false;
        }
        String r = recebida.trim().toLowerCase(Locale.ROOT);
        if (r.startsWith("sha256=")) {
            r = r.substring(7);
        }
        return MessageDigest.isEqual(hmacSha256(segredo, corpo).getBytes(StandardCharsets.US_ASCII), r.getBytes(StandardCharsets.US_ASCII));
    }

    public static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Endereço para onde o CO-TI vai mandar dados: só http(s) e nunca o próprio servidor, o endereço de metadados
     * da nuvem (169.254.169.254) ou faixas especiais. Redes internas (10.x, 192.168.x) são permitidas: integrar
     * com sistemas da empresa é o caso normal.
     *
     * @return null se pode; senão, o motivo
     */
    public static String motivoUrlProibida(String url) {
        URI u;
        try {
            u = URI.create(url.trim());
        } catch (Exception e) {
            return "endereço inválido";
        }
        if (u.getScheme() == null || !(u.getScheme().equalsIgnoreCase("http") || u.getScheme().equalsIgnoreCase("https"))) {
            return "use http:// ou https://";
        }
        if (u.getHost() == null || u.getUserInfo() != null) {
            return "endereço inválido";
        }
        try {
            for (InetAddress a : InetAddress.getAllByName(u.getHost())) {
                if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) {
                    return "endereço local ou reservado não é permitido";
                }
            }
        } catch (UnknownHostException e) {
            return "servidor não encontrado: " + u.getHost();
        }
        return null;
    }

}
