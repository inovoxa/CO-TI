package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GovernancaDominioTest {

    @Test
    void cpfECnpj() {
        assertTrue(Documento.valido(Documento.normalizar("529.982.247-25")));
        assertFalse(Documento.valido(Documento.normalizar("529.982.247-24")));
        assertFalse(Documento.valido("11111111111"), "sequência repetida passa na conta mas não existe");
        assertTrue(Documento.valido(Documento.normalizar("11.222.333/0001-81")));
        assertFalse(Documento.valido(Documento.normalizar("11.222.333/0001-82")));
        assertFalse(Documento.valido("123"));
        assertNull(Documento.normalizar("  "));
    }

    @Test
    void cnpjAlfanumerico() {
        // exemplo publicado pela Receita Federal para o novo formato
        assertTrue(Documento.valido(Documento.normalizar("12.ABC.345/01DE-35")));
        assertTrue(Documento.valido(Documento.normalizar("12.abc.345/01de-35")), "minúsculas são normalizadas");
        assertFalse(Documento.valido(Documento.normalizar("12.ABC.345/01DE-36")));
        assertFalse(Documento.valido("12ABC34501DEAB"), "dígitos verificadores continuam numéricos");
    }

    private static Projetos.Tarefa t(int situacao, int pct, int horas) {
        return new Projetos.Tarefa(situacao, pct, BigDecimal.valueOf(horas));
    }

    @Test
    void percentualDoProjeto() {
        assertEquals(0, Projetos.percentual(List.of()));
        // média simples quando falta hora prevista em alguma tarefa
        assertEquals(50, Projetos.percentual(List.of(t(Projetos.TAREFA_CONCLUIDA, 0, 0), t(Projetos.TAREFA_A_FAZER, 0, 10))));
        // ponderada pelas horas: 100% de 30h + 0% de 10h = 75%
        assertEquals(75, Projetos.percentual(List.of(t(Projetos.TAREFA_CONCLUIDA, 0, 30), t(Projetos.TAREFA_A_FAZER, 0, 10))));
        // cancelada não conta
        assertEquals(40, Projetos.percentual(List.of(t(Projetos.TAREFA_EM_ANDAMENTO, 40, 8), t(Projetos.TAREFA_CANCELADA, 0, 80))));
    }

    @Test
    void situacaoEPercentualDaTarefaAndamJuntos() {
        assertEquals(100, Projetos.percentualDaTarefa(Projetos.TAREFA_CONCLUIDA, 30));
        assertEquals(99, Projetos.percentualDaTarefa(Projetos.TAREFA_EM_ANDAMENTO, 100), "só concluída tem 100%");
        assertEquals(Projetos.TAREFA_CONCLUIDA, Projetos.situacaoDaTarefa(Projetos.TAREFA_EM_ANDAMENTO, 100));
        assertEquals(Projetos.TAREFA_EM_ANDAMENTO, Projetos.situacaoDaTarefa(Projetos.TAREFA_A_FAZER, 10));
        assertEquals(Projetos.TAREFA_CANCELADA, Projetos.situacaoDaTarefa(Projetos.TAREFA_CANCELADA, 100));
    }

}
