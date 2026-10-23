package cn.iocoder.yudao.module.identidade.seguranca;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CifradorSegredosTest {

    private final CifradorSegredos cifrador = new CifradorSegredos(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void idaEVolta() {
        String c = cifrador.cifrar("JBSWY3DPEHPK3PXP", "mfa:1");
        assertTrue(c.startsWith("v1:"));
        assertNotEquals(c, cifrador.cifrar("JBSWY3DPEHPK3PXP", "mfa:1"), "IV aleatório: cifras diferentes");
        assertEquals("JBSWY3DPEHPK3PXP", cifrador.decifrar(c, "mfa:1"));
    }

    @Test
    void contextoDiferenteNaoDecifra() {
        String c = cifrador.cifrar("segredo", "mfa:1");
        assertThrows(IllegalStateException.class, () -> cifrador.decifrar(c, "mfa:2"));
    }

    @Test
    void dadoAdulteradoNaoDecifra() {
        String c = cifrador.cifrar("segredo", "mfa:1");
        char[] cs = c.toCharArray();
        cs[10] = cs[10] == 'A' ? 'B' : 'A';
        assertThrows(IllegalStateException.class, () -> cifrador.decifrar(new String(cs), "mfa:1"));
    }

    @Test
    void semChaveRecusaComMensagemClara() {
        CifradorSegredos sem = new CifradorSegredos("");
        assertFalse(sem.configurado());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> sem.cifrar("x", "y"));
        assertTrue(e.getMessage().contains("CO_TI_CHAVE_MESTRA"));
    }

    @Test
    void chaveDeTamanhoErradoImpedePartida() {
        assertThrows(IllegalStateException.class,
                () -> new CifradorSegredos(Base64.getEncoder().encodeToString(new byte[16])));
    }

}
