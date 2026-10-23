package cn.iocoder.yudao.module.itsm.dominio;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Saneamento do texto formatado de chamados e acompanhamentos (vem do editor da tela, do e-mail e do WhatsApp).
 * Mantém formatação, listas, tabelas, links e imagens; remove script, eventos (onclick...), estilos e
 * protocolos perigosos (javascript:, data:).
 */
public final class HtmlSeguro {

    private static final Safelist PERMITIDO = Safelist.relaxed()
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto", "tel")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    private static final Document.OutputSettings SAIDA = new Document.OutputSettings().prettyPrint(false);

    private HtmlSeguro() {
    }

    public static String limpar(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // base fictícia: links relativos (anexos do próprio portal) continuam válidos
        String limpo = Jsoup.clean(html, "https://portal.local", PERMITIDO, SAIDA);
        return limpo.replace("https://portal.local/", "/");
    }

    /** Texto puro, para títulos, histórico e notificações. */
    public static String texto(String html) {
        return html == null ? "" : Jsoup.parse(html).text();
    }

}
