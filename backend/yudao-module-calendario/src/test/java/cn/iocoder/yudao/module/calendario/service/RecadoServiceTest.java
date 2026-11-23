package cn.iocoder.yudao.module.calendario.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecadoServiceTest {

    private static RecadoService.RecadoReq recado(LocalDate inicio, LocalDate fim) {
        RecadoService.RecadoReq r = new RecadoService.RecadoReq();
        r.setTitulo("  Vacinação  ");
        r.setTexto("\n Campanha na sexta. \n");
        r.setInicio(inicio);
        r.setFim(fim);
        r.setUnidades(null);
        return r;
    }

    @Test
    void semUltimoDiaValeSoOPrimeiroESemUnidadeEhGeral() {
        LocalDate d = LocalDate.of(2026, 11, 23);
        RecadoService.RecadoReq r = RecadoService.normalizar(recado(d, null));
        assertEquals(d, r.getFim());
        assertEquals("Vacinação", r.getTitulo());
        assertEquals("Campanha na sexta.", r.getTexto());
        assertTrue(r.getUnidades().isEmpty());
    }

    @Test
    void periodoInvalidoRecusa() {
        LocalDate d = LocalDate.of(2026, 11, 23);
        assertThrows(ServiceException.class, () -> RecadoService.normalizar(recado(d, d.minusDays(1))));
        assertThrows(ServiceException.class, () -> RecadoService.normalizar(recado(d, d.plusDays(RecadoService.DIAS_MAXIMOS + 1))));
        assertDoesNotThrow(() -> RecadoService.normalizar(recado(d, d.plusDays(RecadoService.DIAS_MAXIMOS))));
    }

    @Test
    void cienteVoltaSoQuandoMudaOQueSeLe() {
        LocalDate d = LocalDate.of(2026, 11, 23);
        Map<String, Object> antes = Map.of("titulo", "Vacinação", "texto", "Campanha na sexta.",
                "inicio", Date.valueOf(d), "fim", Date.valueOf(d.plusDays(2)));
        RecadoService.RecadoReq igual = RecadoService.normalizar(recado(d, d.plusDays(2)));
        igual.setCor("#67c23a");
        assertFalse(RecadoService.mudouConteudo(antes, igual), "trocar a cor ou as unidades não pede Ciente de novo");
        assertTrue(RecadoService.mudouConteudo(antes, RecadoService.normalizar(recado(d, d.plusDays(3)))));
        RecadoService.RecadoReq texto = RecadoService.normalizar(recado(d, d.plusDays(2)));
        texto.setTexto("Campanha no sábado.");
        assertTrue(RecadoService.mudouConteudo(antes, texto));
    }

}
