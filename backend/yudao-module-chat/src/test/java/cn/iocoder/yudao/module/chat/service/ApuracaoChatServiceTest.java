package cn.iocoder.yudao.module.chat.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ApuracaoChatServiceTest {

    private static ApuracaoChatService.Filtro filtro() {
        ApuracaoChatService.Filtro f = new ApuracaoChatService.Filtro();
        f.setMotivo("Processo 123/2026");
        return f;
    }

    @Test
    void semPessoaConversaOuTextoRecusa() {
        assertThrows(ServiceException.class, () -> ApuracaoChatService.consulta(filtro()));
    }

    @Test
    void periodoInvertidoRecusa() {
        ApuracaoChatService.Filtro f = filtro();
        f.setConversaId(1L);
        f.setInicio(LocalDate.of(2026, 5, 2));
        f.setFim(LocalDate.of(2026, 5, 1));
        assertThrows(ServiceException.class, () -> ApuracaoChatService.consulta(f));
    }

    @Test
    void pessoaPodeSerDasConversasOuSoDoQueEnviou() {
        ApuracaoChatService.Filtro f = filtro();
        f.setPessoaId(7L);
        var todas = ApuracaoChatService.consulta(f);
        assertTrue(todas.where().contains("chat.participante"));
        f.setSomenteEnviadas(true);
        var enviadas = ApuracaoChatService.consulta(f);
        assertTrue(enviadas.where().contains("m.remetente_id = ?"));
        assertEquals(7L, enviadas.args().get(2));
    }

    @Test
    void textoProcuraTambemNasVersoesAnterioresESemCuringas() {
        ApuracaoChatService.Filtro f = filtro();
        f.setTexto(" 100%_certo ");
        var c = ApuracaoChatService.consulta(f);
        assertTrue(c.where().contains("chat.mensagem_historico"));
        assertEquals("%100\\%\\_certo%", c.args().get(2));
        assertEquals(c.args().get(2), c.args().get(3));
        assertFalse(c.where().contains("certo"));
    }

}
