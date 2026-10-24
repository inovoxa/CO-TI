package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    void modeloEscapaValoresMenosOsHtmlSaneados() {
        String r = Email.preencher("<b>{{chamado.titulo}}</b>{{{conteudo}}}{{nada}}",
                Map.of("chamado.titulo", "<img src=x onerror=alert(1)>", "conteudo", "<p>ok</p>"));
        assertEquals("<b>&lt;img src=x onerror=alert(1)&gt;</b><p>ok</p>", r);
    }

    @Test
    void assuntoNaoQuebraCabecalho() {
        assertEquals("[CO-TI #7] a Bcc: x@y & z",
                Email.preencherAssunto("[CO-TI #{{chamado.id}}] {{chamado.titulo}}", Map.of("chamado.id", "7", "chamado.titulo", "a\r\nBcc: x@y & z")));
    }

    @Test
    void achaOChamadoNaResposta() {
        assertEquals(Optional.of(123L), Email.chamadoCitado("RE: [CO-TI #123] Impressora", null, null));
        assertEquals(Optional.of(9L), Email.chamadoCitado("Res: Res: [Chamado #9]", null, null));
        assertEquals(Optional.of(55L), Email.chamadoCitado("Sem número", "<coti.55.900.ab@empresa.gov.br>", null));
        assertEquals(Optional.of(56L), Email.chamadoCitado(null, null, "<abc@x> <coti.chamado.56@empresa.gov.br>"));
        assertEquals(Optional.empty(), Email.chamadoCitado("Pedido #12 sem colchetes", "<outro@x>", null));
    }

    @Test
    void ignoraRespostasAutomaticas() {
        assertTrue(Email.automatico(Map.of("auto-submitted", "auto-replied"), "ana@x").isPresent());
        assertTrue(Email.automatico(Map.of("precedence", "bulk"), "ana@x").isPresent());
        assertTrue(Email.automatico(Map.of("x-co-ti-chamado", "3"), "ana@x").isPresent());
        assertTrue(Email.automatico(Map.of(), "MAILER-DAEMON@empresa").isPresent());
        assertTrue(Email.automatico(Map.of("auto-submitted", "no"), "ana@x").isEmpty());
    }

    @Test
    void autenticacaoDoRemetente() {
        assertTrue(Email.remetenteAutenticado("mx.empresa; spf=pass smtp.mailfrom=x; dkim=pass header.d=x; dmarc=pass"));
        assertFalse(Email.remetenteAutenticado("mx.empresa; spf=fail; dkim=none; dmarc=fail"));
        assertFalse(Email.remetenteAutenticado(null));
    }

    @Test
    void cortaACitacaoETrataAssunto() {
        assertEquals("<p>Ainda não funciona</p><p>",
                Email.semCitacao("<p>Ainda não funciona</p><p>" + Email.MARCA_RESPOSTA + "</p><p>antigo</p>"));
        assertEquals("Impressora", Email.titulo("RE: Enc: Impressora"));
        assertEquals("(sem assunto)", Email.titulo("  "));
        assertEquals("<p>a &lt;b&gt;<br>c</p>", Email.textoParaHtml("a <b>\r\nc"));
    }

}
