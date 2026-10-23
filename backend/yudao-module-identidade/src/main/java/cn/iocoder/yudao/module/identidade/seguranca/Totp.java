package cn.iocoder.yudao.module.identidade.seguranca;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * TOTP (RFC 6238) com HMAC-SHA1, 6 dígitos e passo de 30 s — o que Google Authenticator, Microsoft Authenticator,
 * FreeOTP e afins entendem.
 */
public final class Totp {

    public static final int DIGITOS = 6;
    public static final long PASSO_SEGUNDOS = 30;
    /** Aceita o passo anterior e o seguinte para tolerar relógio do celular um pouco fora. */
    public static final int JANELA = 1;

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** Segredo novo de 160 bits, em Base32 (formato do QR code). */
    public static String novoSegredo() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return base32(b);
    }

    public static String uri(String emissor, String conta, String segredoBase32) {
        String e = urlEncode(emissor);
        return "otpauth://totp/" + e + ":" + urlEncode(conta) + "?secret=" + segredoBase32 + "&issuer=" + e
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASSO_SEGUNDOS;
    }

    public static long passo(long epochSegundos) {
        return Math.floorDiv(epochSegundos, PASSO_SEGUNDOS);
    }

    /**
     * @return o passo em que o código bateu (para impedir reuso), ou vazio se não bateu em nenhum da janela
     */
    public static OptionalLong verificar(String segredoBase32, String codigo, long epochSegundos) {
        if (codigo == null || codigo.length() != DIGITOS || !codigo.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        byte[] chave = deBase32(segredoBase32);
        long atual = passo(epochSegundos);
        for (long p = atual - JANELA; p <= atual + JANELA; p++) {
            // comparação em tempo constante
            if (MessageDigest.isEqual(gerar(chave, p).getBytes(), codigo.getBytes())) {
                return OptionalLong.of(p);
            }
        }
        return OptionalLong.empty();
    }

    static String gerar(byte[] chave, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(chave, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%0" + DIGITOS + "d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] dados) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : dados) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] deBase32(String texto) {
        String t = texto.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(t.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : t.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Segredo Base32 inválido");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

}
