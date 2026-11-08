package cn.iocoder.yudao.module.acesso.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LinkRegrasTest {

    @Test
    void aceitaEnderecosComunsEInternos() {
        assertEquals("https://portal.gov.br/a?b=1", LinkRegras.normalizar("https://portal.gov.br/a?b=1").orElseThrow());
        assertEquals("http://10.0.0.5:8080/sistema", LinkRegras.normalizar(" http://10.0.0.5:8080/sistema ").orElseThrow());
        assertEquals("https://portal.gov.br", LinkRegras.normalizar("portal.gov.br").orElseThrow());
        assertEquals("https://intranet:8443/x", LinkRegras.normalizar("intranet:8443/x").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "JAVASCRIPT:alert(1)", "javascript://x/%0aalert(1)", "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)", "file:///etc/passwd", "ftp://a.gov.br", "https://user:senha@a.gov.br", "https://", "com espaco.gov.br", "", "   "})
    void recusaOQueNaoEPagina(String url) {
        assertTrue(LinkRegras.normalizar(url).isEmpty(), url);
    }

    @Test
    void recusaVazio() {
        assertTrue(LinkRegras.normalizar(null).isEmpty());
    }

}
