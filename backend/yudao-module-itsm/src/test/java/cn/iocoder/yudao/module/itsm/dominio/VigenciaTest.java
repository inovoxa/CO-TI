package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static cn.iocoder.yudao.module.itsm.dominio.Vigencia.*;
import static org.junit.jupiter.api.Assertions.*;

class VigenciaTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

    @Test
    void contratoSimplesVaiAteOUltimoDiaDoPeriodo() {
        Estado e = calcular(INICIO, 12, 0, NAO_RENOVA, 30, false, LocalDate.of(2026, 6, 1));
        assertEquals(LocalDate.of(2026, 12, 31), e.fim());
        assertNull(e.prazoAviso());
        assertEquals(e.fim(), e.prazo());
        assertEquals(LocalDate.of(2026, 12, 1), e.dataAlerta());
        assertEquals(Situacao.VIGENTE, e.situacao());
    }

    @Test
    void situacaoAoLongoDoTempo() {
        assertEquals(Situacao.FUTURO, calcular(INICIO, 12, 0, NAO_RENOVA, 30, false, LocalDate.of(2025, 12, 31)).situacao());
        assertEquals(Situacao.VIGENTE, calcular(INICIO, 12, 0, NAO_RENOVA, 30, false, LocalDate.of(2026, 11, 30)).situacao());
        assertEquals(Situacao.A_VENCER, calcular(INICIO, 12, 0, NAO_RENOVA, 30, false, LocalDate.of(2026, 12, 1)).situacao());
        assertEquals(Situacao.A_VENCER, calcular(INICIO, 12, 0, NAO_RENOVA, 30, false, LocalDate.of(2026, 12, 31)).situacao());
        assertEquals(Situacao.VENCIDO, calcular(INICIO, 12, 0, NAO_RENOVA, 30, false, LocalDate.of(2027, 1, 1)).situacao());
        assertEquals(Situacao.ENCERRADO, calcular(INICIO, 12, 0, NAO_RENOVA, 30, true, LocalDate.of(2026, 6, 1)).situacao());
    }

    @Test
    void avisoPrevioAntecipaOPrazoEOAlerta() {
        Estado e = calcular(INICIO, 12, 2, EXPRESSA, 15, false, LocalDate.of(2026, 6, 1));
        assertEquals(LocalDate.of(2026, 11, 1), e.prazoAviso(), "avisar até 2 meses antes do dia seguinte ao fim");
        assertEquals(e.prazoAviso(), e.prazo());
        assertEquals(LocalDate.of(2026, 10, 17), e.dataAlerta());
        assertEquals(Situacao.A_VENCER, calcular(INICIO, 12, 2, EXPRESSA, 15, false, LocalDate.of(2026, 10, 17)).situacao());
    }

    @Test
    void tacitaRenovaSozinhaDepoisDoPrazo() {
        // sem aviso prévio: no dia seguinte ao fim, já é o período 2
        Estado e = calcular(INICIO, 12, 0, TACITA, 30, false, LocalDate.of(2027, 1, 1));
        assertEquals(2, e.periodo());
        assertEquals(LocalDate.of(2027, 12, 31), e.fim());
        assertEquals(Situacao.VIGENTE, e.situacao());
        // no último dia do período 1, ainda é o período 1
        assertEquals(1, calcular(INICIO, 12, 0, TACITA, 30, false, LocalDate.of(2026, 12, 31)).periodo());
    }

    @Test
    void tacitaComAvisoPassaParaOProximoPeriodoQuandoOPrazoDeAvisarPassa() {
        // prazo para cancelar: 1º/11/2026; perdido o prazo, o contrato de 2027 está garantido
        Estado ultimoDia = calcular(INICIO, 12, 2, TACITA, 30, false, LocalDate.of(2026, 11, 1));
        assertEquals(1, ultimoDia.periodo());
        assertEquals(Situacao.A_VENCER, ultimoDia.situacao());
        Estado depois = calcular(INICIO, 12, 2, TACITA, 30, false, LocalDate.of(2026, 11, 2));
        assertEquals(2, depois.periodo());
        assertEquals(LocalDate.of(2027, 12, 31), depois.fim());
        assertEquals(LocalDate.of(2027, 11, 1), depois.prazoAviso());
        assertEquals(Situacao.VIGENTE, depois.situacao(), "o próximo prazo está longe");
    }

    @Test
    void expressaENaoRenovaVencem() {
        assertEquals(Situacao.VENCIDO, calcular(INICIO, 6, 0, EXPRESSA, 30, false, LocalDate.of(2026, 7, 1)).situacao());
        assertEquals(LocalDate.of(2026, 6, 30), calcular(INICIO, 6, 0, NAO_RENOVA, 30, false, LocalDate.of(2027, 1, 1)).fim());
    }

    @Test
    void fimDeMesCurto() {
        // 31/01 + 1 mês = 28/02 (o java ajusta para o último dia); fim = 27/02
        assertEquals(LocalDate.of(2026, 2, 27), calcular(LocalDate.of(2026, 1, 31), 1, 0, NAO_RENOVA, 0, false, LocalDate.of(2026, 2, 1)).fim());
        assertThrows(IllegalArgumentException.class, () -> calcular(INICIO, 0, 0, NAO_RENOVA, 0, false, INICIO));
    }

}
