package cn.iocoder.yudao.module.monitoramento.service.verificador;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Pacotes de Redis (RESP), MQTT 3.1.1 e RADIUS (RFC 2865), sem dependência de servidor. */
final class Protocolos {

    private Protocolos() {
    }

    // ================================================================================== Redis (RESP)

    static byte[] comandoRedis(String... partes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("*" + partes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (String p : partes) {
            byte[] b = p.getBytes(StandardCharsets.UTF_8);
            out.writeBytes(("$" + b.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(b);
            out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        return out.toByteArray();
    }

    /** Uma linha de resposta simples (+OK, -ERR ..., +PONG). */
    static String linha(InputStream in) throws IOException {
        StringBuilder s = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                s.append((char) c);
            }
            if (s.length() > 4096) {
                break;
            }
        }
        if (c == -1 && s.isEmpty()) {
            throw new IOException("conexão fechada pelo servidor");
        }
        return s.toString();
    }

    // ================================================================================== MQTT 3.1.1

    static byte[] mqttConnect(String clienteId, String usuario, String senha, int keepAliveS) {
        ByteArrayOutputStream v = new ByteArrayOutputStream();
        texto(v, "MQTT");
        v.write(4); // 3.1.1
        int flags = 0x02; // clean session
        if (usuario != null) {
            flags |= 0x80;
            if (senha != null) {
                flags |= 0x40;
            }
        }
        v.write(flags);
        v.write(keepAliveS >> 8);
        v.write(keepAliveS & 0xff);
        texto(v, clienteId);
        if (usuario != null) {
            texto(v, usuario);
            if (senha != null) {
                texto(v, senha);
            }
        }
        return pacote(0x10, v.toByteArray());
    }

    static byte[] mqttSubscribe(int id, String topico) {
        ByteArrayOutputStream v = new ByteArrayOutputStream();
        v.write(id >> 8);
        v.write(id & 0xff);
        texto(v, topico);
        v.write(0); // QoS 0
        return pacote(0x82, v.toByteArray());
    }

    static byte[] mqttDisconnect() {
        return new byte[]{(byte) 0xe0, 0};
    }

    static byte[] pacote(int cabecalho, byte[] corpo) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(cabecalho);
        int n = corpo.length;
        do {
            int b = n % 128;
            n /= 128;
            out.write(n > 0 ? b | 0x80 : b);
        } while (n > 0);
        out.writeBytes(corpo);
        return out.toByteArray();
    }

    /** Lê um pacote MQTT: [tipo e flags, corpo...] (o comprimento variável já tirado). */
    static byte[] lerPacoteMqtt(InputStream in) throws IOException {
        int cab = in.read();
        if (cab < 0) {
            throw new IOException("conexão fechada pelo servidor");
        }
        int n = 0, mult = 1, b, lidos = 0;
        do {
            b = in.read();
            if (b < 0 || ++lidos > 4) {
                throw new IOException("pacote MQTT inválido");
            }
            n += (b & 0x7f) * mult;
            mult *= 128;
        } while ((b & 0x80) != 0);
        if (n > 1024 * 1024) {
            throw new IOException("pacote MQTT grande demais");
        }
        byte[] corpo = in.readNBytes(n);
        if (corpo.length < n) {
            throw new IOException("conexão fechada pelo servidor");
        }
        byte[] out = new byte[n + 1];
        out[0] = (byte) cab;
        System.arraycopy(corpo, 0, out, 1, n);
        return out;
    }

    /** Conteúdo de um PUBLISH já lido por {@link #lerPacoteMqtt}. */
    static String conteudoPublish(byte[] p) {
        int qos = (p[0] >> 1) & 0x03;
        int t = ((p[1] & 0xff) << 8) | (p[2] & 0xff);
        int inicio = 3 + t + (qos > 0 ? 2 : 0);
        return new String(p, inicio, Math.max(0, p.length - inicio), StandardCharsets.UTF_8);
    }

    private static void texto(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write(b.length >> 8);
        out.write(b.length & 0xff);
        out.writeBytes(b);
    }

    // ================================================================================== RADIUS (RFC 2865)

    static final int ACCESS_REQUEST = 1, ACCESS_ACCEPT = 2, ACCESS_REJECT = 3, ACCESS_CHALLENGE = 11;

    /** Access-Request com PAP, NAS-Identifier e Message-Authenticator (exigido desde o BlastRADIUS). */
    static byte[] radiusPedido(int id, byte[] autenticador, String usuario, String senha, String segredo) {
        ByteArrayOutputStream attrs = new ByteArrayOutputStream();
        atributo(attrs, 1, usuario.getBytes(StandardCharsets.UTF_8));
        atributo(attrs, 2, esconderSenha(senha.getBytes(StandardCharsets.UTF_8), segredo.getBytes(StandardCharsets.UTF_8), autenticador));
        atributo(attrs, 32, "CO-TI".getBytes(StandardCharsets.UTF_8));
        int posMa = attrs.size() + 20 + 2;
        atributo(attrs, 80, new byte[16]);
        byte[] a = attrs.toByteArray();
        byte[] p = new byte[20 + a.length];
        p[0] = ACCESS_REQUEST;
        p[1] = (byte) id;
        p[2] = (byte) (p.length >> 8);
        p[3] = (byte) (p.length & 0xff);
        System.arraycopy(autenticador, 0, p, 4, 16);
        System.arraycopy(a, 0, p, 20, a.length);
        byte[] ma = hmacMd5(segredo.getBytes(StandardCharsets.UTF_8), p);
        System.arraycopy(ma, 0, p, posMa, 16);
        return p;
    }

    /** RFC 2865, 5.2: blocos de 16 bytes, cada um com XOR de MD5(segredo + anterior). */
    static byte[] esconderSenha(byte[] senha, byte[] segredo, byte[] autenticador) {
        int n = Math.max(16, (senha.length + 15) / 16 * 16);
        byte[] p = Arrays.copyOf(senha, n);
        byte[] out = new byte[n];
        byte[] anterior = autenticador;
        for (int i = 0; i < n; i += 16) {
            byte[] b = md5(concat(segredo, anterior));
            for (int j = 0; j < 16; j++) {
                out[i + j] = (byte) (p[i + j] ^ b[j]);
            }
            anterior = Arrays.copyOfRange(out, i, i + 16);
        }
        return out;
    }

    /** A resposta veio de quem conhece o segredo? MD5(código + id + comprimento + autenticador do pedido + atributos + segredo). */
    static boolean respostaAutentica(byte[] resp, int n, byte[] autenticadorPedido, String segredo) {
        if (n < 20) {
            return false;
        }
        byte[] copia = Arrays.copyOf(resp, n);
        byte[] recebido = Arrays.copyOfRange(copia, 4, 20);
        System.arraycopy(autenticadorPedido, 0, copia, 4, 16);
        byte[] esperado = md5(concat(copia, segredo.getBytes(StandardCharsets.UTF_8)));
        return MessageDigest.isEqual(recebido, esperado);
    }

    /** Reply-Message (18) da resposta, se houver. */
    static String mensagem(byte[] resp, int n) {
        int i = 20;
        while (i + 2 <= n) {
            int t = resp[i] & 0xff, l = resp[i + 1] & 0xff;
            if (l < 2 || i + l > n) {
                break;
            }
            if (t == 18) {
                return new String(resp, i + 2, l - 2, StandardCharsets.UTF_8);
            }
            i += l;
        }
        return null;
    }

    private static void atributo(ByteArrayOutputStream out, int tipo, byte[] valor) {
        if (valor.length > 253) {
            throw new IllegalArgumentException("atributo RADIUS grande demais");
        }
        out.write(tipo);
        out.write(valor.length + 2);
        out.writeBytes(valor);
    }

    static byte[] md5(byte[] b) {
        try {
            return MessageDigest.getInstance("MD5").digest(b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmacMd5(byte[] chave, byte[] dados) {
        try {
            Mac mac = Mac.getInstance("HmacMD5");
            mac.init(new SecretKeySpec(chave, "HmacMD5"));
            return mac.doFinal(dados);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

}
