package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.itsm.dominio.Estoque.*;
import static org.junit.jupiter.api.Assertions.*;

class EstoqueTest {

    private static String unidade(String situacao, String mov, String pessoa, String motivo) {
        return erro(UNIDADE, situacao, 1, mov, 1, pessoa, motivo);
    }

    @Test
    void cicloDeUmCelular() {
        assertNull(unidade(DISPONIVEL, RETIRADA, "Pessoa", null));
        assertEquals(EM_USO, aplicar(UNIDADE, DISPONIVEL, 1, RETIRADA, 1).situacao());
        assertNull(unidade(EM_USO, DEVOLUCAO, null, null));
        assertEquals(DISPONIVEL, aplicar(UNIDADE, EM_USO, 1, DEVOLUCAO, 1).situacao());
        assertNull(unidade(EM_USO, MANUTENCAO_MOV, null, "Tela quebrada"));
        assertEquals(DISPONIVEL, aplicar(UNIDADE, MANUTENCAO, 1, RETORNO, 1).situacao());
    }

    @Test
    void naoRetiraItemQueJaEstaComAlguem() {
        assertNotNull(unidade(EM_USO, RETIRADA, "Outra pessoa", null));
    }

    @Test
    void retiradaExigePessoaEBaixaExigeMotivo() {
        assertNotNull(unidade(DISPONIVEL, RETIRADA, " ", null));
        assertNotNull(unidade(EM_USO, BAIXA, null, ""));
    }

    @Test
    void baixaVemDeQualquerSituacaoEEFinal() {
        assertNull(unidade(EM_USO, BAIXA, null, "Furto"));
        assertEquals(new Novo(BAIXADO, 0), aplicar(UNIDADE, EM_USO, 1, BAIXA, 1));
        assertNotNull(unidade(BAIXADO, RETORNO, null, null));
    }

    @Test
    void pecaIndividualMovimentaUmaDeCadaVez() {
        assertNotNull(erro(UNIDADE, DISPONIVEL, 1, RETIRADA, 2, "Pessoa", null));
        assertNotNull(unidade(DISPONIVEL, ENTRADA, null, null));
    }

    @Test
    void saldoPorQuantidade() {
        assertNull(erro(QUANTIDADE, DISPONIVEL, 5, RETIRADA, 5, "Pessoa", null));
        assertEquals(0, aplicar(QUANTIDADE, DISPONIVEL, 5, RETIRADA, 5).quantidade());
        assertEquals(8, aplicar(QUANTIDADE, DISPONIVEL, 5, ENTRADA, 3).quantidade());
        assertTrue(erro(QUANTIDADE, DISPONIVEL, 5, RETIRADA, 6, "Pessoa", null).contains("insuficiente"));
        assertNotNull(erro(QUANTIDADE, DISPONIVEL, 5, BAIXA, 6, null, "Queimou"));
        assertNotNull(erro(QUANTIDADE, DISPONIVEL, 5, RETIRADA, 0, "Pessoa", null));
    }

    @Test
    void quantidadeNaoTemManutencao() {
        assertNotNull(erro(QUANTIDADE, DISPONIVEL, 5, MANUTENCAO_MOV, 1, null, "x"));
    }

}
