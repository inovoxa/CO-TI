package cn.iocoder.yudao.module.chat.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatRegrasTest {

    @Test
    void conversaDiretaEUmaSoPorPar() {
        assertEquals("3:9", ChatRegras.parChave(9, 3));
        assertEquals(ChatRegras.parChave(3, 9), ChatRegras.parChave(9, 3));
    }

    @Test
    void soExtensoesDaLista() {
        assertTrue(ChatRegras.extensaoPermitida("relatorio.PDF"));
        assertFalse(ChatRegras.extensaoPermitida("virus.exe"));
        assertFalse(ChatRegras.extensaoPermitida("foto.jpg.exe"));
        assertFalse(ChatRegras.extensaoPermitida("pagina.html"));
        assertFalse(ChatRegras.extensaoPermitida("imagem.svg"));
        assertFalse(ChatRegras.extensaoPermitida("semextensao"));
    }

    @Test
    void conteudoPrecisaCombinar() {
        assertTrue(ChatRegras.conteudoCombina("a.pdf", "%PDF-1.7".getBytes()));
        assertFalse(ChatRegras.conteudoCombina("a.pdf", "MZ executavel".getBytes()));
        assertFalse(ChatRegras.conteudoCombina("a.png", "<html>".getBytes()));
        assertTrue(ChatRegras.conteudoCombina("a.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1}));
        assertFalse(ChatRegras.conteudoCombina("a.docx", new byte[]{'M', 'Z'}));
        assertTrue(ChatRegras.conteudoCombina("a.txt", "qualquer".getBytes()));
    }

    @Test
    void nomeNaoLevaCaminho() {
        assertEquals("segredo.txt", ChatRegras.nomeSeguro("../../etc/segredo.txt"));
        assertEquals("a.txt", ChatRegras.nomeSeguro("C:\\pasta\\a.txt"));
        assertEquals("a_b.txt", ChatRegras.nomeSeguro("a\nb.txt"));
        String longo = ChatRegras.nomeSeguro("x".repeat(300) + ".pdf");
        assertEquals(200, longo.length());
        assertTrue(longo.endsWith(".pdf"));
    }

    @Test
    void mimeVemDaExtensao() {
        assertEquals("image/png", ChatRegras.mime("a.PNG"));
        assertEquals("application/octet-stream", ChatRegras.mime("a.docx"));
    }

    @Test
    void ausenciaRespeitaOPeriodo() {
        java.time.LocalDate hoje = java.time.LocalDate.of(2026, 11, 4);
        assertTrue(ChatRegras.ausenciaVale(true, null, null, hoje));
        assertTrue(ChatRegras.ausenciaVale(true, hoje, hoje, hoje));
        assertFalse(ChatRegras.ausenciaVale(false, null, null, hoje));
        assertFalse(ChatRegras.ausenciaVale(true, hoje.plusDays(1), null, hoje));
        assertFalse(ChatRegras.ausenciaVale(true, null, hoje.minusDays(1), hoje));
        assertFalse(ChatRegras.ausenciaVale(null, null, null, hoje));
    }

    @Test
    void mencoesIdaEVolta() {
        assertEquals("1,2", ChatRegras.juntar(List.of(1L, 2L)));
        assertNull(ChatRegras.juntar(List.of()));
        assertEquals(List.of(1L, 2L), ChatRegras.separar("1,2"));
        assertEquals(List.of(), ChatRegras.separar(null));
    }

}
