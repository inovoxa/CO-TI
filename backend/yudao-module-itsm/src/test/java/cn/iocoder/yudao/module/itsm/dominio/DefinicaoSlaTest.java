package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefinicaoSlaTest {

    private static final LocalDateTime SEG_10H = LocalDateTime.of(2026, 10, 26, 10, 0);

    @Test
    void semCalendarioContaCorrido() {
        DefinicaoSla s = new DefinicaoSla(4, "hora", null, false);
        assertEquals(SEG_10H.plusHours(4), s.prazo(SEG_10H, 0));
        assertEquals(SEG_10H.plusHours(5), s.prazo(SEG_10H, 3600), "pendência soma direto");
        assertEquals(7200, s.ativoEntre(SEG_10H, SEG_10H.plusHours(2)));
    }

    @Test
    void comCalendarioContaExpediente() {
        DefinicaoSla s = new DefinicaoSla(4, "hora", Calendario.comercial(List.of()), false);
        assertEquals(LocalDateTime.of(2026, 10, 26, 15, 0), s.prazo(SEG_10H, 0));
        assertEquals(LocalDateTime.of(2026, 10, 26, 17, 0), s.prazo(SEG_10H, 2 * 3600));
        assertEquals(2 * 3600, s.ativoEntre(SEG_10H.plusHours(1), SEG_10H.plusHours(4)), "11h–14h com almoço = 2 h");
    }

    @Test
    void conversaoDeUnidades() {
        assertEquals(90 * 60, new DefinicaoSla(90, "minuto", null, false).segundos());
        assertEquals(2 * 86400, new DefinicaoSla(2, "dia", null, false).segundos());
        assertEquals(30 * 86400, new DefinicaoSla(1, "mes", null, false).segundos());
    }

    @Test
    void validacoes() {
        assertThrows(IllegalArgumentException.class, () -> new DefinicaoSla(1, "semana", null, false));
        assertThrows(IllegalArgumentException.class, () -> new DefinicaoSla(-1, "hora", null, false));
        assertThrows(IllegalArgumentException.class, () -> new DefinicaoSla(4, "hora", null, true));
    }

    @Test
    void calendarioSemExpedienteViraVinteQuatroPorSete() {
        DefinicaoSla s = new DefinicaoSla(4, "hora", new Calendario(Map.of(), List.of()), false);
        assertNull(s.calendario());
        assertEquals(SEG_10H.plusHours(4), s.prazo(SEG_10H, 0));
    }

}
