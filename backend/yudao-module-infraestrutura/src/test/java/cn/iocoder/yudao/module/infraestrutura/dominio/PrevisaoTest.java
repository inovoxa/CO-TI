package cn.iocoder.yudao.module.infraestrutura.dominio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrevisaoTest {

    @Test
    void semReservaValeODiscoDoServidor() {
        Previsao.Disco d = Previsao.disco(1000, 250, 100, 0);
        assertEquals(750, d.usadoBytes());
        assertEquals(250, d.livreBytes());
        assertEquals(75, d.percentual());
        assertFalse(d.pelaReserva());
    }

    @Test
    void comReservaOLivreNuncaPassaDoQueOServidorInforma() {
        // reserva de 1000 com 100 usados, mas o disco só tem 50 livres
        Previsao.Disco d = Previsao.disco(100_000, 50, 100, 1000);
        assertEquals(50, d.livreBytes());
        assertEquals(95, d.percentual());
        assertTrue(d.pelaReserva());
    }

    @Test
    void reservaEstouradaNaoDaLivreNegativo() {
        Previsao.Disco d = Previsao.disco(100_000, 90_000, 1500, 1000);
        assertEquals(0, d.livreBytes());
        assertEquals(100, d.percentual());
    }

    @Test
    void poucosDiasNaoMostramCrescimento() {
        assertEquals(0, Previsao.crescimentoPorDia(100, 900, 2));
        assertEquals(100, Previsao.crescimentoPorDia(100, 900, 8));
    }

    @Test
    void previsaoSoComCrescimentoReal() {
        assertNull(Previsao.diasAteEncher(1000, 0));
        assertNull(Previsao.diasAteEncher(1000, -5));
        assertEquals(10, Previsao.diasAteEncher(1000, 100));
    }

    @Test
    void niveisDeAviso() {
        assertEquals(0, Previsao.nivelDeAviso(79));
        assertEquals(80, Previsao.nivelDeAviso(80));
        assertEquals(90, Previsao.nivelDeAviso(95));
    }

}
