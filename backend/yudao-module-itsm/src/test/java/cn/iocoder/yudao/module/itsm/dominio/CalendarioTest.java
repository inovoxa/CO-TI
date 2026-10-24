package cn.iocoder.yudao.module.itsm.dominio;

import cn.iocoder.yudao.module.itsm.dominio.Calendario.Feriado;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Expediente comercial: segunda a sexta, 08–12 e 13–18 (9 h por dia). 26/10/2026 é uma segunda-feira.
 */
class CalendarioTest {

    private static final long H = 3600;
    private static final Calendario COMERCIAL = Calendario.comercial(List.of());

    private static LocalDateTime t(int dia, int mes, int hora, int min) {
        return LocalDateTime.of(2026, mes, dia, hora, min);
    }

    // ------------------------------------------------------------------ horas úteis

    @Test
    void horasUteisPulamAlmoco() {
        assertEquals(t(26, 10, 15, 0), COMERCIAL.somar(t(26, 10, 10, 0), 4 * H, 0, false, false));
    }

    @Test
    void horasUteisAtravessamOFimDeSemana() {
        // sexta 17:00 + 2 h = 1 h na sexta + 1 h na segunda
        assertEquals(t(2, 11, 9, 0), COMERCIAL.somar(t(30, 10, 17, 0), 2 * H, 0, false, false));
        // aberto no sábado: começa a contar na segunda 08:00
        assertEquals(t(2, 11, 9, 0), COMERCIAL.somar(t(31, 10, 10, 0), H, 0, false, false));
    }

    @Test
    void prazoQueEncaixaNoDiaInteiroVaiParaOInicioDoProximo() {
        // igual ao GLPI: segunda 08:00 + 9 h termina na terça 08:00 (não há expediente entre 18:00 e 08:00)
        assertEquals(t(27, 10, 8, 0), COMERCIAL.somar(t(26, 10, 8, 0), 9 * H, 0, false, false));
    }

    @Test
    void feriadoNaoConta() {
        Calendario c = Calendario.comercial(List.of(new Feriado(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27), false)));
        assertEquals(t(28, 10, 9, 0), c.somar(t(26, 10, 17, 0), 2 * H, 0, false, false));
    }

    @Test
    void feriadoPerpetuoValeTodoAno() {
        Calendario c = Calendario.comercial(List.of(new Feriado(LocalDate.of(2000, 12, 25), LocalDate.of(2000, 12, 25), true)));
        assertTrue(c.feriado(LocalDate.of(2026, 12, 25)));
        assertTrue(c.feriado(LocalDate.of(2031, 12, 25)));
        // quinta 24/12 17:00 + 2 h: sexta 25 é feriado, sábado e domingo não têm expediente
        assertEquals(t(28, 12, 9, 0), c.somar(t(24, 12, 17, 0), 2 * H, 0, false, false));
    }

    @Test
    void feriadoPerpetuoQueViraOAno() {
        Calendario c = Calendario.comercial(List.of(new Feriado(LocalDate.of(2000, 12, 31), LocalDate.of(2001, 1, 2), true)));
        assertTrue(c.feriado(LocalDate.of(2026, 12, 31)));
        assertTrue(c.feriado(LocalDate.of(2027, 1, 1)));
        assertTrue(c.feriado(LocalDate.of(2027, 1, 2)));
        assertFalse(c.feriado(LocalDate.of(2027, 1, 3)));
    }

    // ------------------------------------------------------------------ dias úteis

    @Test
    void diasUteisMantemAHora() {
        assertEquals(t(28, 10, 10, 0), COMERCIAL.somar(t(26, 10, 10, 0), 2 * Calendario.DIA, 0, true, false));
        // sexta + 1 dia útil = segunda
        assertEquals(t(2, 11, 10, 0), COMERCIAL.somar(t(30, 10, 10, 0), Calendario.DIA, 0, true, false));
    }

    @Test
    void diasUteisDepoisDoExpedienteVaiParaOFimDoExpediente() {
        assertEquals(t(27, 10, 18, 0), COMERCIAL.somar(t(26, 10, 19, 0), Calendario.DIA, 0, true, false));
    }

    @Test
    void diasUteisAbertoNoFimDeSemanaComecaNaSegunda() {
        assertEquals(t(3, 11, 8, 0), COMERCIAL.somar(t(31, 10, 10, 0), Calendario.DIA, 0, true, false));
    }

    // ------------------------------------------------------------------ fim do expediente

    @Test
    void fimDoExpediente() {
        // como no GLPI: o dia da abertura conta como o primeiro dia útil
        assertEquals(t(27, 10, 18, 0), COMERCIAL.somar(t(26, 10, 10, 0), Calendario.DIA, 0, true, true));
        assertEquals(t(26, 10, 18, 0), COMERCIAL.somar(t(26, 10, 10, 0), 0, 0, true, true));
        // aberto na sexta, 1 dia: sexta conta, termina segunda 18:00
        assertEquals(t(2, 11, 18, 0), COMERCIAL.somar(t(30, 10, 10, 0), Calendario.DIA, 0, true, true));
    }

    // ------------------------------------------------------------------ tempo adicional e tempo ativo

    @Test
    void pendenciaEmpurraOPrazoEmHorasUteis() {
        assertEquals(t(26, 10, 17, 0), COMERCIAL.somar(t(26, 10, 10, 0), 4 * H, 2 * H, false, false));
        // prazo em dias + 3 h de pendência: terça 10:00 + 3 h úteis = terça 14:00
        assertEquals(t(27, 10, 14, 0), COMERCIAL.somar(t(26, 10, 10, 0), Calendario.DIA, 3 * H, true, false));
    }

    @Test
    void tempoAtivoEntreDoisInstantes() {
        assertEquals(2 * H, COMERCIAL.ativoEntre(t(26, 10, 11, 0), t(26, 10, 14, 0)));
        assertEquals(2 * H, COMERCIAL.ativoEntre(t(30, 10, 17, 0), t(2, 11, 9, 0)));
        assertEquals(0, COMERCIAL.ativoEntre(t(31, 10, 9, 0), t(1, 11, 17, 0)), "fim de semana inteiro");
        assertEquals(5 * 9 * H, COMERCIAL.ativoEntre(t(26, 10, 0, 0), t(31, 10, 0, 0)), "semana cheia");
        assertEquals(0, COMERCIAL.ativoEntre(t(26, 10, 14, 0), t(26, 10, 11, 0)), "fim antes do início");
    }

    @Test
    void emExpediente() {
        assertTrue(COMERCIAL.emExpediente(t(26, 10, 8, 0)));
        assertFalse(COMERCIAL.emExpediente(t(26, 10, 12, 30)));
        assertFalse(COMERCIAL.emExpediente(t(26, 10, 18, 0)), "o fim do período não está dentro");
        assertFalse(COMERCIAL.emExpediente(t(31, 10, 10, 0)));
    }

    @Test
    void calendarioSemExpedienteRecusa() {
        Calendario vazio = new Calendario(java.util.Map.of(), List.of());
        assertFalse(vazio.temDiaUtil());
        assertThrows(IllegalStateException.class, () -> vazio.somar(t(26, 10, 10, 0), H, 0, false, false));
    }

}
