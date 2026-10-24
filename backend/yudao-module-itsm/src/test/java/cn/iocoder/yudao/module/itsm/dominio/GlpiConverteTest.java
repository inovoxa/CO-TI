package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlpiConverteTest {

    @Test
    void htmlDoGlpi10EscapadoEDoGlpi11Puro() {
        assertEquals("<p>Sem <b>rede</b></p>", GlpiConverte.html("&lt;p&gt;Sem &lt;b&gt;rede&lt;/b&gt;&lt;/p&gt;"));
        assertEquals("<p>A &amp; B</p>", GlpiConverte.html("<p>A &amp; B</p>"), "HTML puro fica como está");
        assertEquals("", GlpiConverte.html(null));
        assertEquals("texto simples", GlpiConverte.html("texto simples"));
    }

    @Test
    void documentosCitados() {
        String h = "<p><img src=\"/front/document.send.php?docid=12&amp;itemtype=Ticket&amp;items_id=5\" alt=\"x\">"
                + "<a href=\"https://glpi.empresa/front/document.send.php?itemtype=Ticket&docid=7\">log</a></p>";
        assertEquals(Set.of(12L, 7L), GlpiConverte.documentos(h));
    }

    @Test
    void trocaEnderecosEAvisaOQueNaoVeio() {
        String h = "<p><img src=\"/front/document.send.php?docid=12&amp;tickets_id=5\" width=\"100\">"
                + "<img src=\"https://glpi.empresa/front/document.send.php?docid=13\">"
                + "<a href=\"https://glpi.empresa/front/document.send.php?docid=14\">planilha</a></p>";
        String r = GlpiConverte.trocarDocumentos(h, Map.of(12L, "/admin-api/infra/file/50000/get/a/b.png")::get);
        assertTrue(r.contains("src=\"/admin-api/infra/file/50000/get/a/b.png\""), r);
        assertTrue(r.contains("width=\"100\""), "o resto da tag fica");
        assertTrue(r.contains("[imagem do GLPI — documento 13 não importada]"), r);
        assertTrue(r.contains("href=\"#\">planilha"), "link sem arquivo perde o destino: " + r);
        assertFalse(r.contains("glpi.empresa"), r);
        assertEquals("<p>nada</p>", GlpiConverte.trocarDocumentos("<p>nada</p>", id -> null));
    }

    @Test
    void escalasESituacoes() {
        assertEquals(1, GlpiConverte.escala(0));
        assertEquals(5, GlpiConverte.escala(9));
        assertEquals(6, GlpiConverte.prioridade(6));
        assertEquals(1, GlpiConverte.statusChamado(99));
        assertEquals(9, GlpiConverte.statusMudanca(2), "mudança não usa 2/3: vira em avaliação");
        assertEquals(14, GlpiConverte.statusMudanca(14));
        assertEquals(1, GlpiConverte.estadoTarefa(7));
    }

    @Test
    void notaENomes() {
        assertEquals(4, GlpiConverte.nota(4, null));
        assertEquals(3, GlpiConverte.nota(7, 2.6), "usa a nota já convertida para 5");
        assertNull(GlpiConverte.nota(0, 0.0), "0 = não respondeu");
        assertEquals("Ana Souza", GlpiConverte.nome("Ana", "Souza", "ana.souza"));
        assertEquals("ana.souza", GlpiConverte.nome(" ", null, "ana.souza"));
        assertEquals("joao.silva", GlpiConverte.login("Joao.Silva@empresa.local"));
        assertEquals("ana_maria", GlpiConverte.login("ana_maria"));
        assertNull(GlpiConverte.login("ab"), "curto demais para o yudao");
    }

}
