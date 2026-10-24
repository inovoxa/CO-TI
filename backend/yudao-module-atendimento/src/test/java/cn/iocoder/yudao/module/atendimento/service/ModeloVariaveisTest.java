package cn.iocoder.yudao.module.atendimento.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeloVariaveisTest {

    @Test
    void variaveisPosicionaisEmOrdemNumerica() {
        assertEquals(List.of("1", "2"), ModeloService.variaveis("Olá {{2}}, seu chamado {{1}} e de novo {{2}}"));
        assertEquals(List.of(), ModeloService.variaveis("Sem variáveis"));
        assertEquals(List.of(), ModeloService.variaveis(null));
    }

    @Test
    void variaveisComNomeNaOrdemEmQueAparecem() {
        assertEquals(List.of("nome", "numero"), ModeloService.variaveis("Oi {{ nome }}, o chamado {{numero}} foi resolvido"));
    }

    @Test
    void preencheSemInterpretarOValor() {
        List<String> nomes = ModeloService.variaveis("Oi {{1}}, chamado {{2}}");
        assertEquals("Oi Ana $1 \\x, chamado #42", ModeloService.preencher("Oi {{1}}, chamado {{2}}", nomes, List.of("Ana $1 \\x", "#42")));
    }

}
