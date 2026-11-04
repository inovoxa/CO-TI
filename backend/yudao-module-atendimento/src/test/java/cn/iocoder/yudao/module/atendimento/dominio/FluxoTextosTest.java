package cn.iocoder.yudao.module.atendimento.dominio;

import cn.iocoder.yudao.module.itsm.dominio.Formulario;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FluxoTextosTest {

    private static final Formulario.Pergunta CPF = new Formulario.Pergunta("cpf", "CPF", "regex", true, List.of(), "/^[0-9]{11}$/");
    private static final Formulario.Pergunta NASC = new Formulario.Pergunta("data_nascimento", "Data de nascimento", "date", true, List.of(), null);
    private static final Formulario.Pergunta TIPO = new Formulario.Pergunta("tipo_acesso", "Tipo de acesso", "radios", true,
            List.of("Leitura", "Leitura e escrita"), null);
    private static final Formulario.Pergunta PROC = new Formulario.Pergunta("numero_processo", "Processo", "text", false, List.of(), null);

    @Test
    void servicoSoDoPortal() {
        assertEquals("", FluxoTextos.soNoPortal(List.of(), "https://ti.org"));
        assertEquals("Pelo portal: https://ti.org/catalogo\n- 5.1 Login", FluxoTextos.soNoPortal(List.of("5.1 Login"), "https://ti.org/"));
        assertEquals("Pelo portal (Catálogo de serviços)\n- 5.1 Login", FluxoTextos.soNoPortal(List.of("5.1 Login"), " "));
    }

    @Test
    void comandos() {
        assertEquals(FluxoTextos.Comando.MENU, FluxoTextos.comando(" Menu "));
        assertEquals(FluxoTextos.Comando.MENU, FluxoTextos.comando("0"));
        assertEquals(FluxoTextos.Comando.CANCELAR, FluxoTextos.comando("cancelar!"));
        assertEquals(FluxoTextos.Comando.ATENDENTE, FluxoTextos.comando("Falar com atendente"));
        assertNull(FluxoTextos.comando("meu computador não liga"));
        assertEquals(FluxoTextos.Comando.SENHA, FluxoTextos.comando("SENHA"));
        assertEquals(FluxoTextos.Comando.SENHA, FluxoTextos.comando("senha provisória"));
        // pedido novo, não a senha de um chamado: segue para o catálogo
        assertNull(FluxoTextos.comando("esqueci minha senha"));
    }

    @Test
    void respostasDeOpcoesEOpcionais() {
        assertEquals("Leitura e escrita", FluxoTextos.resposta(TIPO, "2"));
        assertEquals("Leitura", FluxoTextos.resposta(TIPO, "leitura"));
        assertEquals("9", FluxoTextos.resposta(TIPO, "9"), "fora da lista: segue para a validação recusar");
        assertNotNull(FluxoTextos.erro(TIPO, "9"));
        assertNull(FluxoTextos.resposta(PROC, "-"), "opcional pulado");
        assertEquals("-", FluxoTextos.resposta(CPF, "-"), "obrigatório não pula");
    }

    @Test
    void validacaoIgualAoPortal() {
        assertNull(FluxoTextos.erro(CPF, "12345678901"));
        assertEquals("Formato inválido", FluxoTextos.erro(CPF, "123.456"));
        assertNull(FluxoTextos.erro(NASC, "15/03/1990"));
        assertEquals("Data inválida (use dd/mm/aaaa)", FluxoTextos.erro(NASC, "31/02/1990"));
        assertEquals("15/03/1990", FluxoTextos.normalizado(NASC, "1990-03-15"));
        assertEquals("12345678901", FluxoTextos.ajustar(CPF, "123.456.789-01"), "pontuação do celular");
        assertEquals("123.456", FluxoTextos.ajustar(CPF, "123.456"), "mesmo sem pontuação não serve: fica como veio");
        assertEquals("15/03/1990", FluxoTextos.ajustar(NASC, "15/03/1990"), "só para perguntas de expressão");
    }

    @Test
    void mascaraEResumo() {
        assertEquals("••••••••901", FluxoTextos.mascarar("cpf", "12345678901"));
        assertEquals("João", FluxoTextos.mascarar("nome_completo", "João"));        Map<String, String> r = new LinkedHashMap<>();
        r.put("cpf", "12345678901");
        r.put("tipo_acesso", "Leitura");
        String s = FluxoTextos.resumo("1.5 Acesso a pasta", List.of(CPF, TIPO, PROC), r);
        assertTrue(s.contains("1. CPF: ••••••••901"));
        assertTrue(s.contains("2. Tipo de acesso: Leitura"));
        assertTrue(s.contains("3. Processo: —"));
        assertFalse(s.contains("12345678901"));
    }

    @Test
    void textoDaPergunta() {
        String t = FluxoTextos.pergunta(TIPO, 3, 15);
        assertTrue(t.startsWith("*3/15* Tipo de acesso"));
        assertTrue(t.contains("1) Leitura\n2) Leitura e escrita"));
        assertTrue(FluxoTextos.pergunta(PROC, 1, 1).contains("opcional"));
        assertEquals("2.11", FluxoTextos.codigo(" 2.11 "));
        assertNull(FluxoTextos.codigo("2.1 impressora"));
        assertEquals(Boolean.TRUE, FluxoTextos.simNao("Sim!"));
        assertEquals(Boolean.FALSE, FluxoTextos.simNao("não"));
    }

}
