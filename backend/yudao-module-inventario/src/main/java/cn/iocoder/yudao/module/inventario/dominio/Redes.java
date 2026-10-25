package cn.iocoder.yudao.module.inventario.dominio;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;

/**
 * Confere se um IP está numa lista de redes CIDR (IPv4 e IPv6). Endereço inválido nunca está.
 */
public final class Redes {

    private Redes() {
    }

    public static boolean permitido(String ip, List<String> redes) {
        if (redes == null || redes.isEmpty()) {
            return true;
        }
        byte[] endereco = bytes(ip);
        if (endereco == null) {
            return false;
        }
        for (String r : redes) {
            if (contem(r == null ? "" : r.trim(), endereco)) {
                return true;
            }
        }
        return false;
    }

    static boolean contem(String cidr, byte[] endereco) {
        int barra = cidr.indexOf('/');
        byte[] rede = bytes(barra < 0 ? cidr : cidr.substring(0, barra));
        if (rede == null) {
            return false;
        }
        // IPv4 mapeado em IPv6 (::ffff:10.0.0.1), como o Tomcat às vezes entrega
        if (endereco.length == 16 && rede.length == 4 && mapeado(endereco)) {
            endereco = java.util.Arrays.copyOfRange(endereco, 12, 16);
        }
        if (rede.length != endereco.length) {
            return false;
        }
        int bits = rede.length * 8;
        int prefixo;
        try {
            prefixo = barra < 0 ? bits : Integer.parseInt(cidr.substring(barra + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (prefixo < 0 || prefixo > bits) {
            return false;
        }
        BigInteger mascara = prefixo == 0 ? BigInteger.ZERO
                : BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).shiftRight(bits - prefixo).shiftLeft(bits - prefixo);
        return new BigInteger(1, rede).and(mascara).equals(new BigInteger(1, endereco).and(mascara));
    }

    private static boolean mapeado(byte[] a) {
        for (int i = 0; i < 10; i++) {
            if (a[i] != 0) {
                return false;
            }
        }
        return (a[10] & 0xff) == 0xff && (a[11] & 0xff) == 0xff;
    }

    /** Só literais: nunca resolve nome (sem DNS). */
    private static byte[] bytes(String ip) {
        if (ip == null || ip.isBlank() || !ip.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(ip.trim()).getAddress();
        } catch (Exception e) {
            return null;
        }
    }

}
