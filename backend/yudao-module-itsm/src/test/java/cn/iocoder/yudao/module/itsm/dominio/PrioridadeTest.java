package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrioridadeTest {

    @Test
    void matrizPadraoIgualADoGlpi() {
        // linha 1 e 5 do priority_matrix instalado pelo GLPI 12
        assertEquals(1, Prioridade.calcular(1, 1));
        assertEquals(2, Prioridade.calcular(1, 5));
        assertEquals(2, Prioridade.calcular(5, 1));
        assertEquals(5, Prioridade.calcular(5, 5));
        assertEquals(3, Prioridade.calcular(3, 3));
        assertEquals(4, Prioridade.calcular(4, 3));
    }

    @Test
    void matrizConfiguradaComFalbackParaMedia() {
        Map<String, Map<String, Integer>> m = Map.of("2", Map.of("2", 5));
        assertEquals(5, Prioridade.calcular(2, 2, m));
        assertEquals(4, Prioridade.calcular(5, 3, m), "sem par na matriz: média arredondada");
    }

    @Test
    void foraDaEscalaRecusa() {
        assertThrows(IllegalArgumentException.class, () -> Prioridade.calcular(0, 3));
        assertThrows(IllegalArgumentException.class, () -> Prioridade.calcular(3, 6));
    }

    @Test
    void rotulos() {
        assertEquals("Média", Prioridade.rotulo(3));
        assertEquals("Crítica", Prioridade.rotulo(6));
    }

}
