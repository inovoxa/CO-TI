package cn.iocoder.yudao.module.atendimento.dominio;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AtendimentoDominioTest {

    @Test
    void telefoneE164() {
        assertEquals("+5516999990000", Telefone.normalizar("(16) 99999-0000"));
        assertEquals("+5516999990000", Telefone.normalizar("5516999990000@s.whatsapp.net"));
        assertEquals("+5516999990000", Telefone.normalizar("+55 16 99999-0000"));
        assertEquals("+351912345678", Telefone.normalizar("00351 912 345 678"));
        assertEquals("+1415555012", Telefone.normalizar("+1 415 555 012"));
        assertNull(Telefone.normalizar("123"));
        assertNull(Telefone.normalizar(null));
    }

    @Test
    void nonoDigitoBrasileiro() {
        assertEquals(Set.of("+5516999990000", "+551699990000"), Telefone.variantes("+5516999990000"));
        assertEquals(Set.of("+551699990000", "+5516999990000"), Telefone.variantes("+551699990000"));
        assertEquals(Set.of("+551633330000"), Telefone.variantes("+551633330000"), "fixo não tem nono dígito");
        assertEquals(Set.of("+351912345678"), Telefone.variantes("+351912345678"));
    }

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 24, 9, 0);

    @Test
    void atribuiQuemTemMenosConversasRespeitandoOLimite() {
        var ana = new Atribuicao.Candidato(1, true, 2, 5, T.minusMinutes(1));
        var beto = new Atribuicao.Candidato(2, true, 1, 2, T.minusMinutes(5));
        var caio = new Atribuicao.Candidato(3, false, 0, 0, null);
        assertEquals(Optional.of(2L), Atribuicao.escolher(List.of(ana, beto, caio), 0), "indisponível não recebe");
        var betoCheio = new Atribuicao.Candidato(2, true, 3, 3, T);
        assertEquals(Optional.of(1L), Atribuicao.escolher(List.of(ana, betoCheio), 3), "no limite da caixa não recebe");
        assertEquals(Optional.empty(), Atribuicao.escolher(List.of(new Atribuicao.Candidato(1, true, 2, 2, T)), 2));
        assertEquals(Optional.empty(), Atribuicao.escolher(List.of(), 0));
    }

    @Test
    void empateVaiParaQuemRecebeuHaMaisTempo() {
        var a = new Atribuicao.Candidato(1, true, 0, 1, T.minusMinutes(1));
        var b = new Atribuicao.Candidato(2, true, 0, 1, T.minusMinutes(30));
        var c = new Atribuicao.Candidato(3, true, 0, 1, null);
        assertEquals(Optional.of(3L), Atribuicao.escolher(List.of(a, b, c), 0), "nunca recebeu vem primeiro");
        assertEquals(Optional.of(2L), Atribuicao.escolher(List.of(a, b), 0));
    }

    @Test
    void disponibilidadePeloSinal() {
        assertTrue(Atribuicao.disponivel("online", T.minusMinutes(2), T));
        assertFalse(Atribuicao.disponivel("online", T.minusMinutes(4), T), "navegador fechado");
        assertFalse(Atribuicao.disponivel("ocupado", T, T));
        assertFalse(Atribuicao.disponivel("online", null, T));
    }

    @Test
    void assinatura() {
        byte[] corpo = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        String h = Webhooks.hmacSha256("segredo", corpo);
        assertEquals(64, h.length());
        assertTrue(Webhooks.assinaturaValida("segredo", corpo, "sha256=" + h));
        assertTrue(Webhooks.assinaturaValida("segredo", corpo, h.toUpperCase()));
        assertFalse(Webhooks.assinaturaValida("outro", corpo, h));
        assertFalse(Webhooks.assinaturaValida("segredo", "{\"a\":2}".getBytes(StandardCharsets.UTF_8), h));
        assertFalse(Webhooks.assinaturaValida("segredo", corpo, null));
    }

    @Test
    void enderecosDeSaida() {
        assertNull(Webhooks.motivoUrlProibida("https://10.0.0.5/receber"), "rede interna da empresa pode");
        assertNotNull(Webhooks.motivoUrlProibida("http://127.0.0.1:8080/x"));
        assertNotNull(Webhooks.motivoUrlProibida("http://localhost/x"));
        assertNotNull(Webhooks.motivoUrlProibida("http://169.254.169.254/latest/meta-data"));
        assertNotNull(Webhooks.motivoUrlProibida("file:///etc/passwd"));
        assertNotNull(Webhooks.motivoUrlProibida("http://user:senha@10.0.0.5/"));
        assertNotNull(Webhooks.motivoUrlProibida("gopher://10.0.0.5/"));
    }

}
