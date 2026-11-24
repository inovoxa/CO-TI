package cn.iocoder.yudao.module.inventario.dominio;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Faixas de IPv4 (CIDR) → unidade: normaliza o que a equipe digita e acha a faixa mais específica de um IP.
 */
public final class Faixas {

    private Faixas() {
    }

    public record Faixa(Long id, String cidr, Long deptId, long rede, int prefixo) {

        public static Faixa de(Long id, String cidr, Long deptId) {
            String n = normalizar(cidr);
            if (n == null) {
                throw new IllegalArgumentException("faixa inválida: " + cidr);
            }
            int barra = n.indexOf('/');
            return new Faixa(id, n, deptId, numero(n.substring(0, barra)), Integer.parseInt(n.substring(barra + 1)));
        }

        boolean contem(long ip) {
            long mascara = prefixo == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixo)) & 0xFFFFFFFFL;
            return (ip & mascara) == rede;
        }
    }

    /**
     * "172.19.21.0/24" fica como está; "172.19.21.0" (sem barra, terminando em .0) vira /24, como a equipe escreve nos
     * nomes; um IP sem barra que não termina em .0 vira /32. O endereço vira o da rede (172.19.21.7/24 → 172.19.21.0/24).
     * Inválido → null.
     */
    public static String normalizar(String cidr) {
        if (cidr == null) {
            return null;
        }
        String c = cidr.trim().replace(" ", "");
        int barra = c.indexOf('/');
        String ip = barra < 0 ? c : c.substring(0, barra);
        long n = numero(ip);
        if (n < 0) {
            return null;
        }
        int prefixo;
        if (barra < 0) {
            prefixo = (n & 0xFF) == 0 ? 24 : 32;
        } else {
            try {
                prefixo = Integer.parseInt(c.substring(barra + 1));
            } catch (NumberFormatException e) {
                return null;
            }
            if (prefixo < 8 || prefixo > 32) {
                return null;
            }
        }
        long mascara = (0xFFFFFFFFL << (32 - prefixo)) & 0xFFFFFFFFL;
        return texto(n & mascara) + "/" + prefixo;
    }

    /** A faixa mais específica (maior prefixo) que contém o IP. */
    public static Optional<Faixa> doIp(String ip, List<Faixa> faixas) {
        long n = numero(ip);
        if (n < 0 || faixas.isEmpty()) {
            return Optional.empty();
        }
        return faixas.stream().filter(f -> f.contem(n)).max(Comparator.comparingInt(Faixa::prefixo));
    }

    /** "a.b.c.0/24" do IP, para agrupar máquinas por rede. */
    public static String rede24(String ip) {
        long n = numero(ip);
        return n < 0 ? null : texto(n & 0xFFFFFF00L) + "/24";
    }

    /** IPv4 → número; inválido → -1. */
    static long numero(String ip) {
        if (ip == null) {
            return -1;
        }
        String[] p = ip.trim().split("\\.", -1);
        if (p.length != 4) {
            return -1;
        }
        long n = 0;
        for (String s : p) {
            if (s.isEmpty() || s.length() > 3 || !s.chars().allMatch(Character::isDigit)) {
                return -1;
            }
            int v = Integer.parseInt(s);
            if (v > 255) {
                return -1;
            }
            n = (n << 8) | v;
        }
        return n;
    }

    private static String texto(long n) {
        return ((n >> 24) & 0xFF) + "." + ((n >> 16) & 0xFF) + "." + ((n >> 8) & 0xFF) + "." + (n & 0xFF);
    }

}
