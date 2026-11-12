package cn.iocoder.yudao.module.calendario.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CalendarioServiceTest {

    @Test
    void quemNasceuEm29DeFevereiroComemoraEm28NosAnosComuns() {
        LocalDate nasc = LocalDate.of(1992, 2, 29);
        assertEquals(LocalDate.of(2026, 2, 28), CalendarioService.diaNoAno(nasc, 2026));
        assertEquals(LocalDate.of(2028, 2, 29), CalendarioService.diaNoAno(nasc, 2028));
        assertEquals(LocalDate.of(2026, 11, 12), CalendarioService.diaNoAno(LocalDate.of(1980, 11, 12), 2026));
    }

    @Test
    void nascimentoNoFuturoOuAnoAbsurdoRecusa() {
        LocalDate hoje = LocalDate.of(2026, 11, 12);
        assertThrows(ServiceException.class, () -> CalendarioService.validarNascimento(hoje.plusDays(1), hoje));
        assertThrows(ServiceException.class, () -> CalendarioService.validarNascimento(LocalDate.of(1850, 1, 1), hoje));
        assertDoesNotThrow(() -> CalendarioService.validarNascimento(LocalDate.of(1990, 5, 3), hoje));
    }

    private static CalendarioService.EventoReq evento(LocalDateTime inicio, LocalDateTime fim, boolean diaInteiro) {
        CalendarioService.EventoReq r = new CalendarioService.EventoReq();
        r.setTitulo("  Reunião  ");
        r.setDescricao("   ");
        r.setInicio(inicio);
        r.setFim(fim);
        r.setDiaInteiro(diaInteiro);
        return r;
    }

    @Test
    void diaInteiroVaiParaOComecoDoDiaETextoVazioViraNulo() {
        var r = CalendarioService.normalizar(evento(LocalDateTime.of(2026, 11, 12, 15, 30),
                LocalDateTime.of(2026, 11, 13, 9, 0), true));
        assertEquals("Reunião", r.getTitulo());
        assertNull(r.getDescricao());
        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0), r.getInicio());
        assertEquals(LocalDateTime.of(2026, 11, 13, 0, 0), r.getFim());
    }

    @Test
    void soDiaHoraOuDiaInteiroPedemNovaConfirmacao() {
        LocalDateTime ini = LocalDateTime.of(2026, 11, 12, 15, 0);
        var r = CalendarioService.normalizar(evento(ini, null, false));
        assertFalse(CalendarioService.mudouHorario(ini, null, false, r));
        assertTrue(CalendarioService.mudouHorario(ini.minusHours(1), null, false, r));
        assertTrue(CalendarioService.mudouHorario(ini, ini.plusHours(1), false, r));
        assertTrue(CalendarioService.mudouHorario(ini, null, true, r));
    }

    @Test
    void quandoNoAviso() {
        assertEquals("12/11 às 15:00", CalendarioService.quando(LocalDateTime.of(2026, 11, 12, 15, 0), false));
        assertEquals("12/11 (dia inteiro)", CalendarioService.quando(LocalDateTime.of(2026, 11, 12, 0, 0), true));
    }

    @Test
    void fimAntesDoInicioRecusa() {
        assertThrows(ServiceException.class, () -> CalendarioService.normalizar(evento(
                LocalDateTime.of(2026, 11, 12, 15, 0), LocalDateTime.of(2026, 11, 12, 14, 0), false)));
    }

}
