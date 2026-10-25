package cn.iocoder.yudao.module.monitoramento.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static cn.iocoder.yudao.module.monitoramento.dominio.Estados.*;
import static org.junit.jupiter.api.Assertions.*;

class MonitoramentoDominioTest {

    @Test
    void primeiraVerificacaoSoAvisaSeCaiu() {
        Transicao t = avaliar(null, 0, 0, true, false, 0, 0);
        assertEquals(NO_AR, t.estado());
        assertTrue(t.importante());
        assertFalse(t.avisar(), "primeira batida no ar não avisa");
        t = avaliar(null, 0, 0, false, false, 0, 0);
        assertEquals(FORA, t.estado());
        assertTrue(t.avisar(), "primeira batida fora avisa");
    }

    @Test
    void tentativasFicamPendentesAntesDeCair() {
        Transicao t = avaliar(NO_AR, 0, 0, false, false, 2, 0);
        assertEquals(PENDENTE, t.estado());
        assertEquals(1, t.tentativa());
        assertFalse(t.importante(), "no ar → pendente não é evento");
        assertFalse(t.avisar());
        t = avaliar(PENDENTE, 1, 0, false, false, 2, 0);
        assertEquals(PENDENTE, t.estado());
        t = avaliar(PENDENTE, 2, 0, false, false, 2, 0);
        assertEquals(FORA, t.estado());
        assertTrue(t.importante() && t.avisar(), "pendente → fora avisa");
        t = avaliar(FORA, 3, 0, true, false, 2, 0);
        assertEquals(NO_AR, t.estado());
        assertEquals(0, t.tentativa());
        assertTrue(t.avisar(), "voltou avisa");
        t = avaliar(PENDENTE, 1, 0, true, false, 2, 0);
        assertFalse(t.avisar(), "pendente → no ar não avisa (não chegou a cair)");
    }

    @Test
    void reavisoACadaNVerificacoesFora() {
        Transicao t = avaliar(FORA, 1, 0, false, false, 0, 3);
        assertEquals(1, t.quedasSeguidas());
        assertFalse(t.reaviso());
        t = avaliar(FORA, 2, 1, false, false, 0, 3);
        t = avaliar(FORA, 3, t.quedasSeguidas(), false, false, 0, 3);
        assertTrue(t.reaviso());
        assertEquals(0, t.quedasSeguidas());
        assertFalse(avaliar(FORA, 3, 2, false, false, 0, 0).reaviso(), "sem reaviso configurado");
    }

    @Test
    void manutencaoNaoAvisaESaidaParaForaAvisa() {
        Transicao t = avaliar(NO_AR, 0, 0, false, true, 0, 0);
        assertEquals(MANUTENCAO, t.estado());
        assertTrue(t.importante());
        assertFalse(t.avisar());
        t = avaliar(MANUTENCAO, 0, 0, false, false, 0, 0);
        assertEquals(FORA, t.estado());
        assertTrue(t.avisar(), "saiu da manutenção fora do ar: avisa");
        t = avaliar(MANUTENCAO, 0, 0, true, false, 0, 0);
        assertTrue(t.importante());
        assertFalse(t.avisar(), "saiu da manutenção no ar: não avisa");
    }

    @Test
    void codigosAceitos() {
        assertTrue(Regras.codigoAceito("200-299", 204));
        assertFalse(Regras.codigoAceito("200-299", 301));
        assertTrue(Regras.codigoAceito("200-299,301", 301));
        assertTrue(Regras.codigoAceito("[\"200-299\",\"401\"]", 401), "lista JSON do Kuma");
        assertTrue(Regras.codigoAceito(null, 200));
        assertTrue(Regras.codigosValidos("200-299, 301"));
        assertFalse(Regras.codigosValidos("abc"));
    }

    @Test
    void consultaJson() {
        String j = "{\"status\":\"ok\",\"dados\":{\"fila\":12,\"servicos\":[{\"nome\":\"db\",\"ativo\":true}]},\"com espaço\":1}";
        assertTrue(Regras.consultaJson(j, "status", "==", "ok").ok());
        assertTrue(Regras.consultaJson(j, "$.dados.fila", "<", "20").ok());
        assertFalse(Regras.consultaJson(j, "dados.fila", ">", "20").ok());
        assertTrue(Regras.consultaJson(j, "dados.servicos[0].ativo", "==", "true").ok());
        assertTrue(Regras.consultaJson(j, "[\"com espaço\"]", "==", "1.0").ok(), "números comparados como número");
        assertTrue(Regras.consultaJson(j, "dados.servicos[0].nome", "contem", "d").ok());
        assertNotNull(Regras.consultaJson(j, "dados.inexistente", "==", "x").erro());
        assertNotNull(Regras.consultaJson("<html>", "status", "==", "ok").erro());
        assertFalse(Regras.caminhoValido("$sum(itens.valor)"), "expressão JSONata não é caminho");
        assertTrue(Regras.caminhoValido(""));
    }

    @Test
    void janelasDeManutencao() {
        LocalDateTime seg10h = LocalDateTime.of(2026, 10, 26, 10, 0); // segunda-feira
        assertTrue(Regras.manutencaoValendo("manual", true, null, null, null, null, null, seg10h));
        assertFalse(Regras.manutencaoValendo("manual", false, null, null, null, null, null, seg10h));
        assertTrue(Regras.manutencaoValendo("unica", true, seg10h.minusHours(1), seg10h.plusHours(1), null, null, null, seg10h));
        assertFalse(Regras.manutencaoValendo("unica", true, seg10h.plusHours(1), seg10h.plusHours(2), null, null, null, seg10h));
        LocalTime t9 = LocalTime.of(9, 0);
        LocalTime t11 = LocalTime.of(11, 0);
        assertTrue(Regras.manutencaoValendo("semanal", true, null, null, "1,3", t9, t11, seg10h));
        assertFalse(Regras.manutencaoValendo("semanal", true, null, null, "2", t9, t11, seg10h));
        // de domingo 23 h a segunda 2 h (atravessa a meia-noite)
        LocalTime t23 = LocalTime.of(23, 0);
        LocalTime t2 = LocalTime.of(2, 0);
        assertTrue(Regras.manutencaoValendo("semanal", true, null, null, "7", t23, t2, LocalDateTime.of(2026, 10, 26, 1, 30)));
        assertTrue(Regras.manutencaoValendo("semanal", true, null, null, "7", t23, t2, LocalDateTime.of(2026, 10, 25, 23, 30)));
        assertFalse(Regras.manutencaoValendo("semanal", true, null, null, "7", t23, t2, LocalDateTime.of(2026, 10, 26, 2, 30)));
    }

    @Test
    void pingEHost() {
        assertEquals(12, Regras.tempoPing("64 bytes from 10.0.0.1: icmp_seq=1 ttl=64 time=12.3 ms"));
        assertEquals(1, Regras.tempoPing("64 bytes from 10.0.0.1: seq=0 ttl=64 time=0.081 ms"));
        assertNull(Regras.tempoPing("1 packets transmitted, 0 received, 100% packet loss"));
        assertTrue(Regras.hostValido("link-osasco.prefeitura.local"));
        assertTrue(Regras.hostValido("10.20.5.1"));
        assertFalse(Regras.hostValido("-c 100 10.0.0.1"), "opção de linha de comando");
        assertFalse(Regras.hostValido("host; rm -rf /"));
    }

}
