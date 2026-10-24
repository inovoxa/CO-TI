package cn.iocoder.yudao.module.atendimento.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvisosTextoTest {

    @Test
    void htmlDoAcompanhamentoViraTextoDoCanal() {
        assertEquals("Troquei o toner.\nTeste & confirme <ok>.",
                AvisosCanal.texto("<p>Troquei o <b>toner</b>.</p><p>Teste &amp; confirme &lt;ok&gt;.</p>"));
        assertEquals("linha 1\nlinha 2", AvisosCanal.texto("linha 1<br/>  <br>linha 2"));
        assertEquals("", AvisosCanal.texto(null));
        assertEquals(1500, AvisosCanal.texto("<p>" + "x".repeat(3000) + "</p>").length());
    }

}
