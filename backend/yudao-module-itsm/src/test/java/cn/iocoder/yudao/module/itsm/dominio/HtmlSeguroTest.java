package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlSeguroTest {

    @Test
    void removeScriptEventosEProtocolosPerigosos() {
        String sujo = "<p onclick=\"roubar()\">Olá<script>alert(1)</script></p>"
                + "<a href=\"javascript:alert(1)\">x</a><img src=\"data:image/png;base64,AAAA\" onerror=\"x()\">"
                + "<iframe src=\"https://mal.example\"></iframe><div style=\"background:url(x)\">y</div>";
        String limpo = HtmlSeguro.limpar(sujo);
        assertFalse(limpo.contains("script"));
        assertFalse(limpo.contains("onclick"));
        assertFalse(limpo.contains("onerror"));
        assertFalse(limpo.contains("javascript:"));
        assertFalse(limpo.contains("data:"));
        assertFalse(limpo.contains("iframe"));
        assertFalse(limpo.contains("style"));
        assertTrue(limpo.contains("Olá"));
    }

    @Test
    void mantemFormatacaoLinksEAnexosDoPortal() {
        String html = "<p><b>Impressora</b> parada</p><ul><li>2º andar</li></ul>"
                + "<a href=\"https://intranet.empresa\">wiki</a><img src=\"/admin-api/infra/file/4/get/a.png\">";
        String limpo = HtmlSeguro.limpar(html);
        assertTrue(limpo.contains("<b>Impressora</b>"));
        assertTrue(limpo.contains("<li>2º andar</li>"));
        assertTrue(limpo.contains("href=\"https://intranet.empresa\""));
        assertTrue(limpo.contains("src=\"/admin-api/infra/file/4/get/a.png\""));
    }

    @Test
    void vazioETextoPuro() {
        assertEquals("", HtmlSeguro.limpar(null));
        assertEquals("", HtmlSeguro.limpar("   "));
        assertEquals("Olá mundo", HtmlSeguro.texto("<p>Olá <b>mundo</b></p>"));
    }

}
