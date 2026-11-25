package cn.iocoder.yudao.module.atendimento.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FluxoChamadoTest {

    @Test
    void numeroDoChamadoSoComCerquilhaEComoMensagemInteira() {
        assertEquals(123L, FluxoService.numeroChamado("#123"));
        assertEquals(123L, FluxoService.numeroChamado(" # 123 "));
        assertNull(FluxoService.numeroChamado("123"));
        assertNull(FluxoService.numeroChamado("o #123 não resolveu"));
        assertNull(FluxoService.numeroChamado(null));
    }

    @Test
    void textoDoCelularViraHtmlEscapado() {
        assertEquals("<p>linha 1<br>&lt;b&gt;linha 2&lt;/b&gt;</p>", FluxoService.paraHtml("  linha 1\n<b>linha 2</b> "));
    }

}
