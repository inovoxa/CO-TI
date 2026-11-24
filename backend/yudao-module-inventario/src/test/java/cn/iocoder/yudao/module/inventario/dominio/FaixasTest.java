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

}
