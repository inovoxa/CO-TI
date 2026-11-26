package cn.iocoder.yudao.module.inventario.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FaixasTest {

    @Test
    void normalizaOQueAEquipeDigita() {
        assertEquals("172.19.21.0/24", Faixas.normalizar("172.19.21.0/24"));
        assertEquals("172.19.21.0/24", Faixas.normalizar(" 172.19.21.0 "), "sem barra e terminando em .0: /24, como nos nomes");
        assertEquals("172.19.21.0/24", Faixas.normalizar("172.19.21.77/24"), "vira o endereço da rede");
        assertEquals("10.0.0.5/32", Faixas.normalizar("10.0.0.5"));
        assertEquals("10.20.0.0/16", Faixas.normalizar("10.20.30.40/16"));
        assertNull(Faixas.normalizar("172.19.21/24"));
        assertNull(Faixas.normalizar("300.1.1.0/24"));
        assertNull(Faixas.normalizar("10.0.0.0/4"), "faixa larga demais");
        assertNull(Faixas.normalizar("10.0.0.0/33"));
        assertNull(Faixas.normalizar("abc"));
    }

    @Test
    void valeAFaixaMaisEspecifica() {
        List<Faixas.Faixa> l = List.of(Faixas.Faixa.de(1L, "172.19.0.0/16", 100L), Faixas.Faixa.de(2L, "172.19.21.0/24", 200L),
                Faixas.Faixa.de(3L, "172.19.21.50/32", 300L));
        assertEquals(200L, Faixas.doIp("172.19.21.7", l).orElseThrow().deptId());
        assertEquals(300L, Faixas.doIp("172.19.21.50", l).orElseThrow().deptId());
        assertEquals(100L, Faixas.doIp("172.19.99.1", l).orElseThrow().deptId());
        assertTrue(Faixas.doIp("10.0.0.1", l).isEmpty());
        assertTrue(Faixas.doIp("fe80::1", l).isEmpty());
        assertTrue(Faixas.doIp(null, l).isEmpty());
        assertEquals("172.19.21.0/24", Faixas.rede24("172.19.21.200"));
    }

    @Test
    void unidadePeloPrimeiroIpQueCaiNumaFaixa() {
        List<Faixas.Faixa> l = List.of(Faixas.Faixa.de(1L, "172.21.9.0/24", 100L), Faixas.Faixa.de(2L, "10.20.5.0/24", 200L));
        // o principal fora das faixas: vale o segundo
        assertEquals(100L, Faixas.doIps("192.168.0.10", "192.168.0.10,172.21.9.105", l).orElseThrow().deptId());
        // os dois em faixas: vale o principal
        assertEquals(200L, Faixas.doIps("10.20.5.31", "172.21.9.105,10.20.5.31", l).orElseThrow().deptId());
        // espaço sobrando e lista sem o principal
        assertEquals(100L, Faixas.doIps(" 8.8.8.8 ", " 172.21.9.7 ", l).orElseThrow().deptId());
        // nenhum numa faixa: sem unidade (sem palpite)
        assertTrue(Faixas.doIps("192.168.0.10", "192.168.0.11", l).isEmpty());
        assertTrue(Faixas.doIps(null, null, l).isEmpty());
        assertEquals(List.of("10.0.0.1", "10.0.0.2"), Faixas.lista("10.0.0.1", "10.0.0.2,10.0.0.1,lixo"));
        assertFalse(Faixas.util("127.0.0.1"));
        assertFalse(Faixas.util("169.254.10.1"));
        assertFalse(Faixas.util("0.0.0.0"));
        assertTrue(Faixas.util("172.21.9.105"));
    }

}
