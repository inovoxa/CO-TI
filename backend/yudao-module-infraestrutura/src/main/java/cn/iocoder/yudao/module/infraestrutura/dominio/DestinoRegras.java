package cn.iocoder.yudao.module.infraestrutura.dominio;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Para onde um webhook de saída pode ir. A rede interna do órgão é permitida, porque é onde ficam os sistemas que costumam receber
 * os avisos. O próprio servidor e os endereços locais de enlace, onde fica o serviço de metadados das nuvens, não são.
 */
public final class DestinoRegras {

    private DestinoRegras() {
    }

    /** @return o motivo da recusa, ou nulo se o destino é permitido */
    public static String motivoDeRecusa(String url) {
        URI u;
        try {
            u = URI.create(url == null ? "" : url.strip());
        } catch (IllegalArgumentException e) {
            return "Endereço inválido";
        }
        if (!"https".equalsIgnoreCase(u.getScheme()) && !"http".equalsIgnoreCase(u.getScheme())) {
            return "Use um endereço http ou https";
        }
        if (u.getHost() == null || u.getRawUserInfo() != null) {
            return "Endereço inválido";
        }
        InetAddress[] enderecos;
        try {
            enderecos = InetAddress.getAllByName(u.getHost());
        } catch (UnknownHostException e) {
            return "O servidor de destino não foi encontrado";
        }
        for (InetAddress a : enderecos) {
            if (bloqueado(a)) {
                return "Este destino não é permitido: o próprio servidor ou endereço local";
            }
        }
        return null;
    }

    static boolean bloqueado(InetAddress a) {
        if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        // IPv6 escrito como IPv4 (::ffff:169.254.169.254 já vira IPv4 no Java; ::169.254.169.254 não)
        if (a instanceof Inet6Address) {
            byte[] b = a.getAddress();
            boolean zeros = true;
            for (int i = 0; i < 12; i++) {
                zeros &= b[i] == 0;
            }
            if (zeros) {
                try {
                    return bloqueado(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

}
