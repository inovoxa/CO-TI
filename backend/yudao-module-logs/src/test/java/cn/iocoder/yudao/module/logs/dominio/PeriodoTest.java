package cn.iocoder.yudao.module.logs.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PeriodoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 27, 14, 30);

    @Test
    void hoje_compara_com_ontem_ate_a_mesma_hora() {
        Periodo p = Periodo.de("hoje", null, null, AGORA);
        assertEquals(LocalDateTime.of(2026, 10, 27, 0, 0), p.de());
        assertEquals(AGORA, p.ate());
        Periodo a = p.anterior();
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), a.de());
        assertEquals(LocalDateTime.of(2026, 10, 26, 14, 30), a.ate());
    }

    @Test
    void ontem_e_o_dia_inteiro() {
        Periodo p = Periodo.de("ontem", null, null, AGORA);
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), p.de());
        assertEquals(LocalDateTime.of(2026, 10, 27, 0, 0), p.ate());
        assertEquals(LocalDateTime.of(2026, 10, 25, 0, 0), p.anterior().de());
    }

    @Test
    void relativo_e_tudo() {
        Periodo p = Periodo.de("1d", null, null, AGORA);
        assertEquals(AGORA.minusDays(1), p.de());
        assertEquals(AGORA.minusDays(2), p.anterior().de());
        assertNull(Periodo.de("tudo", null, null, AGORA).anterior());
        assertThrows(IllegalArgumentException.class, () -> Periodo.de("3x", null, null, AGORA));
    }

}
