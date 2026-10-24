package cn.iocoder.yudao.module.atendimento.dominio;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanaisDominioTest {

    @Test
    void assinaturaDaMeta() {
        byte[] corpo = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        String certa = "sha256=" + Webhooks.hmacSha256("segredo-do-app", corpo);
        assertTrue(Meta.assinaturaValida("segredo-do-app", corpo, certa));
        assertTrue(Meta.assinaturaValida("segredo-do-app", corpo, certa.toUpperCase().replace("SHA256=", "sha256=")), "hex maiúsculo vale");
        assertFalse(Meta.assinaturaValida("outro-segredo", corpo, certa));
        assertFalse(Meta.assinaturaValida("segredo-do-app", "{}".getBytes(StandardCharsets.UTF_8), certa), "corpo alterado");
        assertFalse(Meta.assinaturaValida("segredo-do-app", corpo, certa.substring(7)), "sem o prefixo sha256=");
        assertFalse(Meta.assinaturaValida("segredo-do-app", corpo, null));
        assertFalse(Meta.assinaturaValida(null, corpo, certa));
    }

    @Test
    void comparaSegredosCurtos() {
        assertTrue(Meta.iguais("abc", "abc"));
        assertFalse(Meta.iguais("abc", "abd"));
        assertFalse(Meta.iguais("", ""), "segredo vazio nunca confere");
        assertFalse(Meta.iguais(null, "x"));
    }

    @Test
    void classificaErrosDaMeta() {
        Meta.Erro janela = Meta.classificar(400, Map.of("error", Map.of("code", 131047, "message", "Re-engagement message")));
        assertFalse(janela.passageiro());
        assertEquals(Meta.MSG_JANELA, janela.mensagem());
        assertEquals(Meta.MSG_JANELA, Meta.classificar(400, Map.of("error", Map.of("code", 10, "error_subcode", 2018278))).mensagem(),
                "Instagram fora da janela");
        assertTrue(Meta.classificar(400, Map.of("error", Map.of("code", 130429, "message", "Rate limit hit"))).passageiro());
        assertTrue(Meta.classificar(400, Map.of("error", Map.of("code", 80007))).passageiro());
        assertTrue(Meta.classificar(503, Map.of()).passageiro());
        Meta.Erro token = Meta.classificar(401, Map.of("error", Map.of("code", 190, "message", "Invalid OAuth access token")));
        assertFalse(token.passageiro());
        assertTrue(token.mensagem().startsWith("Token de acesso inválido"));
        Meta.Erro outro = Meta.classificar(400, Map.of("error", Map.of("code", 100, "message", "Invalid parameter",
                "error_data", Map.of("details", "to is invalid"))));
        assertFalse(outro.passageiro());
        assertTrue(outro.mensagem().contains("Invalid parameter: to is invalid"));
    }

    @Test
    void situacaoEMidia() {
        assertEquals("entregue", Meta.situacao("delivered"));
        assertEquals("lida", Meta.situacao("read"));
        assertEquals("falhou", Meta.situacao("failed"));
        assertNull(Meta.situacao("deleted"));
        assertEquals("image", Meta.tipoMidiaWhatsapp("image/png"));
        assertEquals("document", Meta.tipoMidiaWhatsapp("image/webp"), "o que o WhatsApp não mostra como imagem vai como documento");
        assertEquals("audio", Meta.tipoMidiaWhatsapp("audio/ogg"));
        assertEquals("document", Meta.tipoMidiaWhatsapp("application/pdf"));
        assertEquals(".ogg", Meta.extensao("audio/ogg; codecs=opus"));
        assertEquals("imagem", Meta.formato("image/jpeg"));
    }

}
