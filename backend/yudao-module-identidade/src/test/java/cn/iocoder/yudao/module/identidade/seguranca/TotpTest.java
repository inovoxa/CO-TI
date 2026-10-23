package cn.iocoder.yudao.module.identidade.seguranca;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class TotpTest {

    /** Semente SHA-1 do apêndice B da RFC 6238 ("12345678901234567890"). */
    private static final byte[] SEMENTE_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void bateComOsVetoresDaRfc6238() {
        // a RFC traz 8 dígitos; com 6 dígitos valem os 6 últimos
        assertEquals("287082", Totp.gerar(SEMENTE_RFC, Totp.passo(59)));
        assertEquals("081804", Totp.gerar(SEMENTE_RFC, Totp.passo(1111111109)));
        assertEquals("050471", Totp.gerar(SEMENTE_RFC, Totp.passo(1111111111)));
        assertEquals("005924", Totp.gerar(SEMENTE_RFC, Totp.passo(1234567890)));
        assertEquals("279037", Totp.gerar(SEMENTE_RFC, Totp.passo(2000000000)));
    }

    @Test
    void base32IdaEVolta() {
        String b32 = Totp.base32(SEMENTE_RFC);
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", b32);
        assertArrayEquals(SEMENTE_RFC, Totp.deBase32(b32));
    }

    @Test
    void aceitaPassoVizinhoERecusaDistante() {
        String segredo = Totp.base32(SEMENTE_RFC);
        long t = 1111111111;
        String codigo = Totp.gerar(SEMENTE_RFC, Totp.passo(t));
        assertEquals(OptionalLong.of(Totp.passo(t)), Totp.verificar(segredo, codigo, t));
        assertTrue(Totp.verificar(segredo, codigo, t + 30).isPresent());
        assertTrue(Totp.verificar(segredo, codigo, t + 90).isEmpty());
    }

    @Test
    void recusaFormatoErrado() {
        String segredo = Totp.novoSegredo();
        assertTrue(Totp.verificar(segredo, null, 0).isEmpty());
        assertTrue(Totp.verificar(segredo, "12345", 0).isEmpty());
        assertTrue(Totp.verificar(segredo, "12a456", 0).isEmpty());
    }

    @Test
    void uriParaQrCode() {
        String uri = Totp.uri("CO-TI Empresa", "ana.souza", "ABC");
        assertEquals("otpauth://totp/CO-TI%20Empresa:ana.souza?secret=ABC&issuer=CO-TI%20Empresa"
                + "&algorithm=SHA1&digits=6&period=30", uri);
    }

}
