package cn.iocoder.yudao.module.atendimento.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsatTest {

    @Test
    void reconheceNotas() {
        assertEquals(5, Csat.nota("5"));
        assertEquals(4, Csat.nota("  4! "));
        assertEquals(3, Csat.nota("Nota 3"));
        assertEquals(2, Csat.nota("2 estrelas"));
        assertEquals(4, Csat.nota("⭐⭐⭐⭐"));
        assertEquals(5, Csat.nota("⭐️ ⭐️ ⭐️ ⭐️ ⭐️"), "com seletor de variação e espaços");
        assertEquals(1, Csat.nota("★"));
    }

    @Test
    void outraMensagemNaoEhNota() {
        assertNull(Csat.nota("0"));
        assertNull(Csat.nota("6"));
        assertNull(Csat.nota("10"));
        assertNull(Csat.nota("1 problema novo na impressora"));
        assertNull(Csat.nota("obrigado"));
        assertNull(Csat.nota("⭐⭐⭐⭐⭐⭐"));
        assertNull(Csat.nota("⭐ legal"));
        assertNull(Csat.nota(""));
        assertNull(Csat.nota(null));
    }

    @Test
    void mediana() {
        assertNull(Csat.mediana(List.of()));
        assertEquals(30L, Csat.mediana(List.of(10L, 30L, 90L)));
        assertEquals(20L, Csat.mediana(List.of(10L, 30L)));
    }

}
