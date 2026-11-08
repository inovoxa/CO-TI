package cn.iocoder.yudao.module.acesso.dominio;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * Regras dos links, sem banco.
 */
public final class LinkRegras {

    public static final int MAX_PESSOAIS = 10;

    private LinkRegras() {
    }

    /**
     * Endereço pronto para abrir, ou vazio se não servir. Só http e https: "javascript:" e "data:" rodariam código na
     * sessão de quem clica. Sem protocolo, entende https. Usuário e senha no endereço também não entram.
     */
    public static Optional<String> normalizar(String digitado) {
        if (digitado == null || digitado.isBlank()) {
            return Optional.empty();
        }
        String t = digitado.strip();
        if (t.chars().anyMatch(c -> c <= ' ')) {
            return Optional.empty();
        }
        String comProtocolo = t.contains("://") ? t : "https://" + t;
        try {
            URI u = new URI(comProtocolo);
            String esquema = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if (!(esquema.equals("http") || esquema.equals("https")) || u.getHost() == null || u.getRawUserInfo() != null) {
                return Optional.empty();
            }
            return Optional.of(comProtocolo);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

}
