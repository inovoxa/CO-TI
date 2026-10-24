package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuscaTextoTest {

    @Test
    void palavrasViramPrefixosSemAcento() {
        assertEquals("impressora:* & nao:* & imprime:*", BuscaTexto.consulta("Impressora NÃO imprime!"));
        assertEquals("vpn:* | acesso:*", BuscaTexto.consultaQualquer("VPN / acesso"));
    }

    @Test
    void sintaxeDoTsqueryNaoPassa() {
        // operadores e aspas são separadores, nunca chegam ao to_tsquery
        assertEquals("senha:* & admin:*", BuscaTexto.consulta("senha' & !admin:*) | ("));
        assertNull(BuscaTexto.consulta("  & | ! a "));
        assertNull(BuscaTexto.consulta(null));
    }

    @Test
    void trechoEscapadoComDestaque() {
        String t = "Use <script> no " + BuscaTexto.INICIO + "terminal" + BuscaTexto.FIM;
        assertEquals("Use &lt;script&gt; no <mark>terminal</mark>", BuscaTexto.trechoHtml(t));
    }

}
