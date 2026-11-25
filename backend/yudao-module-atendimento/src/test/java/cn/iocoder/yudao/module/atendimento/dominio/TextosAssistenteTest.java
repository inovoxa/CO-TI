package cn.iocoder.yudao.module.atendimento.dominio;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TextosAssistenteTest {

    @Test
    void trocaVariaveisESomeLinhaVazia() {
        Map<String, Object> v = new HashMap<>();
        v.put("chamado", 12);
        v.put("titulo", "VPN");
        v.put("situacao", "Novo");
        v.put("aberto_em", "01/11/2026");
        v.put("solucionado_em", "");
        v.put("ultima_autor", "");
        v.put("ultima_data", "");
        v.put("ultima_texto", null);
        String s = TextosAssistente.preencher(TextosAssistente.item(TextosAssistente.DETALHE).padrao(), v);
        assertEquals("*Chamado #12*\nVPN\nSituação: Novo\nAberto em: 01/11/2026", s);
    }

    @Test
    void variavelDesconhecidaFicaEValorComCifraoNaoQuebra() {
        assertEquals("oi {nome} R$ 5", TextosAssistente.preencher("oi {nome} {valor}", Map.of("valor", "R$ 5")));
        assertEquals("a\n\nb", TextosAssistente.preencher("a\n\n\n\nb\n", Map.of()));
        assertEquals("", TextosAssistente.preencher(null, Map.of()));
    }

    @Test
    void validaConformeOTipo() {
        assertNull(TextosAssistente.validar(TextosAssistente.MOSTRAR_ULTIMA, "true"), "igual ao padrão = volta ao padrão");
        assertEquals("false", TextosAssistente.validar(TextosAssistente.MOSTRAR_ULTIMA, "false"));
        assertThrows(IllegalArgumentException.class, () -> TextosAssistente.validar(TextosAssistente.MOSTRAR_ULTIMA, "talvez"));
        assertEquals("7", TextosAssistente.validar(TextosAssistente.CONSULTA_QUANTIDADE, " 7 "));
        assertThrows(IllegalArgumentException.class, () -> TextosAssistente.validar(TextosAssistente.CONSULTA_QUANTIDADE, "50"));
        assertThrows(IllegalArgumentException.class, () -> TextosAssistente.validar(TextosAssistente.MENU, "   "));
        assertEquals("Menu novo", TextosAssistente.validar(TextosAssistente.MENU, "Menu novo\r\n"));
        assertThrows(IllegalArgumentException.class, () -> TextosAssistente.item("nao.existe"));
    }

    @Test
    void todoTextoTemPadraoEUsaSoAsVariaveisDeclaradas() {
        for (TextosAssistente.Item i : TextosAssistente.itens()) {
            assertFalse(i.padrao().isBlank(), i.chave());
            if (i.tipo() == TextosAssistente.Tipo.TEXTO) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([a-z_]+)}").matcher(i.padrao());
                while (m.find()) {
                    assertTrue(i.variaveis().contains(m.group(1)), i.chave() + " usa {" + m.group(1) + "} sem declarar");
                }
            }
        }
        assertTrue(TextosAssistente.itens().stream().map(TextosAssistente.Item::grupo).toList()
                .containsAll(List.of("menu", "chamado", "avisos", "ia")));
    }

    @Test
    void promptMantemAsRegrasAcimaDasOrientacoes() {
        String s = PromptsIa.sistemaAssistente("Ana", "Fale como pirata", "Assine Equipe TI", List.of());
        assertTrue(s.indexOf("Fale como pirata") < s.indexOf("Regras:"));
        assertTrue(s.indexOf("Regras:") < s.indexOf("Assine Equipe TI"));
        assertTrue(PromptsIa.ferramentas(true).stream().anyMatch(f -> f.toString().contains("adicionar_informacao")));
        assertFalse(PromptsIa.ferramentas(false).stream().anyMatch(f -> f.toString().contains("adicionar_informacao")));
        assertTrue(PromptsIa.ferramentas(true).get(PromptsIa.ferramentas(true).size() - 1).toString().contains("transferir_para_humano"));
    }

}
