package cn.iocoder.yudao.module.infraestrutura.dominio;

import cn.iocoder.yudao.module.infraestrutura.dominio.EntradaRegras.Decisao;
import cn.iocoder.yudao.module.infraestrutura.dominio.EntradaRegras.Fatos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EntradaRegrasTest {

    private static final byte[] SEGREDO = "segredo".getBytes(StandardCharsets.UTF_8);

    private static Fatos fatos(int contas, boolean ativa, boolean protegida, boolean nome, boolean confirmar, int feitos) {
        return new Fatos(contas, ativa, protegida, nome, confirmar, feitos, 5);
    }

    @Test
    void soExecutaComContaUnicaAtivaELiberada() {
        assertEquals(Decisao.EXECUTAR, EntradaRegras.decidir(fatos(1, true, false, false, false, 0)));
        assertEquals(Decisao.NAO_IDENTIFICADA, EntradaRegras.decidir(fatos(0, false, false, false, false, 0)));
        assertEquals(Decisao.VARIAS_CONTAS, EntradaRegras.decidir(fatos(2, true, false, false, false, 0)));
        assertEquals(Decisao.JA_DESATIVADA, EntradaRegras.decidir(fatos(1, false, false, false, false, 0)));
        assertEquals(Decisao.PROTEGIDA, EntradaRegras.decidir(fatos(1, true, true, false, false, 0)));
        assertEquals(Decisao.NOME_DIVERGE, EntradaRegras.decidir(fatos(1, true, false, true, false, 0)));
    }

    @Test
    void limiteEModoPedemConfirmacao() {
        assertEquals(Decisao.EXECUTAR, EntradaRegras.decidir(fatos(1, true, false, false, false, 4)));
        assertEquals(Decisao.CONFIRMAR_LIMITE, EntradaRegras.decidir(fatos(1, true, false, false, false, 5)));
        assertEquals(Decisao.CONFIRMAR_MODO, EntradaRegras.decidir(fatos(1, true, false, false, true, 0)));
    }

    @Test
    void contaProtegidaVenceOLimite() {
        assertEquals(Decisao.PROTEGIDA, EntradaRegras.decidir(fatos(1, true, true, false, false, 99)));
    }

    @Test
    void assinatura() {
        byte[] corpo = "{}".getBytes(StandardCharsets.UTF_8);
        Instant agora = Instant.ofEpochSecond(1_000_000);
        String c = "1000000";
        String a = EntradaRegras.assinar(SEGREDO, c, corpo);
        assertTrue(EntradaRegras.assinaturaValida(SEGREDO, c, corpo, a, agora));
        assertTrue(EntradaRegras.assinaturaValida(SEGREDO, c, corpo, "sha256=" + a.toUpperCase(), agora));
        assertFalse(EntradaRegras.assinaturaValida(SEGREDO, c, "{ }".getBytes(StandardCharsets.UTF_8), a, agora), "corpo alterado");
        assertFalse(EntradaRegras.assinaturaValida("outro".getBytes(StandardCharsets.UTF_8), c, corpo, a, agora), "outro segredo");
        assertFalse(EntradaRegras.assinaturaValida(SEGREDO, c, corpo, a, agora.plusSeconds(301)), "pedido antigo");
        assertFalse(EntradaRegras.assinaturaValida(SEGREDO, "abc", corpo, a, agora));
        assertFalse(EntradaRegras.assinaturaValida(SEGREDO, null, corpo, a, agora));
        assertFalse(EntradaRegras.assinaturaValida(SEGREDO, c, corpo, null, agora));
    }

    @Test
    void lista_de_ips() {
        assertTrue(EntradaRegras.ipPermitido("1.2.3.4", ""));
        assertTrue(EntradaRegras.ipPermitido("10.1.2.3", "10.0.0.0/8"));
        assertTrue(EntradaRegras.ipPermitido("192.168.0.9", "10.0.0.0/8, 192.168.0.9"));
        assertFalse(EntradaRegras.ipPermitido("11.1.2.3", "10.0.0.0/8"));
        assertFalse(EntradaRegras.ipPermitido("192.168.0.10", "192.168.0.9"));
        assertFalse(EntradaRegras.ipPermitido("192.168.0.9", "rh.exemplo.test"), "nome não vale");
        assertFalse(EntradaRegras.ipPermitido("x", "10.0.0.0/8"));
        assertFalse(EntradaRegras.ipPermitido("10.0.0.1", "10.0.0.0/99"));
        assertTrue(EntradaRegras.ipPermitido("2001:db8::1", "2001:db8::/32"));
    }

    @Test
    void leCaminhosDoJson() throws Exception {
        var j = new ObjectMapper().readTree("{\"f\":{\"m\":123,\"n\":\" Ana \",\"v\":\"\",\"o\":{\"x\":1},\"z\":null}}");
        assertEquals(Optional.of("123"), EntradaRegras.ler(j, "f.m"));
        assertEquals(Optional.of("Ana"), EntradaRegras.ler(j, "f.n"));
        assertTrue(EntradaRegras.ler(j, "f.v").isEmpty());
        assertTrue(EntradaRegras.ler(j, "f.o").isEmpty());
        assertTrue(EntradaRegras.ler(j, "f.z").isEmpty());
        assertTrue(EntradaRegras.ler(j, "f.nada.mais").isEmpty());
        assertTrue(EntradaRegras.ler(j, "").isEmpty());
    }

    @Test
    void comparaNomesEValores() {
        assertTrue(EntradaRegras.mesmoNome("JOÃO  da Silva", "joao da silva"));
        assertFalse(EntradaRegras.mesmoNome("João da Silva", "João Silva"));
        assertTrue(EntradaRegras.contem("DESLIGADO, BLOQUEADO", "bloqueado"));
        assertFalse(EntradaRegras.contem("DESLIGADO", "ATIVO"));
        assertTrue(EntradaRegras.loginProtegido("Administrator", null));
        assertTrue(EntradaRegras.loginProtegido("svc.backup", "svc.backup,svc.x"));
        assertFalse(EntradaRegras.loginProtegido("ana", "svc.backup"));
    }

    @Test
    void entendeDatas() {
        assertEquals("2026-11-05T00:00", EntradaRegras.data("2026-11-05").orElseThrow().toString());
        assertEquals("2026-11-05T00:00", EntradaRegras.data("05/11/2026").orElseThrow().toString());
        assertEquals("2026-11-05T14:30", EntradaRegras.data("2026-11-05T14:30:00").orElseThrow().toString());
        assertTrue(EntradaRegras.data("amanhã").isEmpty());
        assertTrue(EntradaRegras.data("").isEmpty());
    }

}
