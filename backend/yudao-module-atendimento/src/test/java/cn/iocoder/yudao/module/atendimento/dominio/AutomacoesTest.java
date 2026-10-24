package cn.iocoder.yudao.module.atendimento.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AutomacoesTest {

    private static Automacoes.Contexto ctx(String conteudo, Long agente, Set<Long> etiquetas, boolean expediente) {
        return new Automacoes.Contexto(7L, "whatsapp_cloud", 0, null, agente, etiquetas, conteudo, "rita@prefeitura.gov.br",
                "+5516988887777", false, () -> expediente);
    }

    private static Automacoes.Condicao c(String campo, String op, Object valor) {
        return new Automacoes.Condicao(campo, op, valor);
    }

    @Test
    void conteudoSemAcentoNemMaiusculaEComVariosTermos() {
        var x = ctx("Minha SENHA expirou, não consigo entrar", null, Set.of(), true);
        assertTrue(Automacoes.atende(List.of(c("conteudo", "contem", "impressora, senha")), x));
        assertTrue(Automacoes.atende(List.of(c("conteudo", "contem", "nao consigo")), x), "sem acento casa com acento");
        assertFalse(Automacoes.atende(List.of(c("conteudo", "nao_contem", "senha")), x));
        assertTrue(Automacoes.atende(List.of(c("conteudo", "comeca_com", "minha")), x));
        assertFalse(Automacoes.atende(List.of(c("conteudo", "contem", " , ")), x), "termos vazios não casam com tudo");
    }

    @Test
    void todasAsCondicoesPrecisamValer() {
        var x = ctx("oi", null, Set.of(3L), false);
        assertTrue(Automacoes.atende(List.of(), x), "sem condições: sempre");
        assertTrue(Automacoes.atende(List.of(c("caixa", "em", List.of(7, 8)), c("atendente", "vazio", null), c("horario", "fora", null)), x));
        assertFalse(Automacoes.atende(List.of(c("caixa", "em", List.of(7)), c("horario", "dentro", null)), x));
        assertTrue(Automacoes.atende(List.of(c("caixa", "fora_de", List.of("9")), c("canal", "em", List.of("whatsapp_cloud"))), x));
        assertTrue(Automacoes.atende(List.of(c("etiqueta", "tem", List.of(3, 4)), c("etiqueta", "nao_tem", List.of(5))), x));
        assertTrue(Automacoes.atende(List.of(c("contato_email", "contem", "@prefeitura.gov.br"), c("contato_portal", "nao", null)), x));
        assertTrue(Automacoes.atende(List.of(c("contato_telefone", "comeca_com", "+55 16")), x));
        assertTrue(Automacoes.atende(List.of(c("status", "em", List.of(0)), c("prioridade", "vazio", null)), x));
    }

    @Test
    void horarioSoECalculadoSePerguntado() {
        var x = new Automacoes.Contexto(1L, "api", 0, null, null, Set.of(), "", null, null, false,
                () -> { throw new AssertionError("não devia consultar o calendário"); });
        assertTrue(Automacoes.atende(List.of(c("canal", "em", List.of("api"))), x));
    }

    @Test
    void validacao() {
        assertNull(Automacoes.problemaCondicao(c("conteudo", "contem", "senha")));
        assertNotNull(Automacoes.problemaCondicao(c("conteudo", "em", "senha")), "operador não serve para o campo");
        assertNotNull(Automacoes.problemaCondicao(c("caixa", "em", List.of())));
        assertNotNull(Automacoes.problemaCondicao(c("inventado", "em", 1)));
        assertNull(Automacoes.problemaCondicao(c("horario", "fora", null)));
        assertNull(Automacoes.problemaAcao(new Automacoes.Acao("status", "resolvida")));
        assertNotNull(Automacoes.problemaAcao(new Automacoes.Acao("status", "apagada")));
        assertNotNull(Automacoes.problemaAcao(new Automacoes.Acao("prioridade", 9)));
        assertNotNull(Automacoes.problemaAcao(new Automacoes.Acao("enviar_mensagem", " ")));
        assertNotNull(Automacoes.problemaAcao(new Automacoes.Acao("apagar_tudo", null)));
    }

    @Test
    void variaveisDoTexto() {
        assertEquals("Olá Rita, conversa #12 com Ana. $1",
                Automacoes.preencher("Olá {{contato.nome}}, conversa #{{ conversa.id }} com {{atendente.nome}}. $1",
                        Map.of("contato.nome", "Rita", "conversa.id", "12", "atendente.nome", "Ana")));
    }

}
