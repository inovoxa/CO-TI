package cn.iocoder.yudao.module.itsm.dominio;

import cn.iocoder.yudao.module.itsm.dominio.Formulario.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormularioTest {

    /** Trecho do formulário "Criação de usuário" do projeto anterior. */
    private static final Definicao CRIACAO = new Definicao(List.of(
            new Secao("Dados do colaborador", List.of(
                    new Pergunta("nome_completo", "Nome completo", "text", true, null, null),
                    new Pergunta("data_nascimento", "Nascimento", "date", true, null, null),
                    new Pergunta("cpf", "CPF", "regex", true, null, "/^[0-9]{11}$/"),
                    new Pergunta("email_superior", "E-mail do superior", "email", true, null, null),
                    new Pergunta("observacao", "Observação", "textarea", false, null, null))),
            new Secao("Acessos", List.of(
                    new Pergunta("usuario_estagiario", "Estagiário?", "radios", true, List.of("nao", "sim"), null)))));

    @Test
    void respostasValidasSaoNormalizadas() {
        Resultado r = Formulario.validar(CRIACAO, Map.of("nome_completo", "  Ana Souza ", "data_nascimento", "1990-05-03",
                "cpf", "12345678901", "email_superior", "Chefe@Empresa.Gov.Br", "usuario_estagiario", "nao", "extra", "x"));
        assertTrue(r.valido(), r.erros().toString());
        assertEquals("Ana Souza", r.respostas().get("nome_completo"));
        assertEquals("03/05/1990", r.respostas().get("data_nascimento"), "data no padrão brasileiro");
        assertEquals("chefe@empresa.gov.br", r.respostas().get("email_superior"));
        assertFalse(r.respostas().containsKey("extra"), "campo que não existe no formulário é descartado");
        assertFalse(r.respostas().containsKey("observacao"), "opcional vazio não entra");
    }

    @Test
    void errosPorPergunta() {
        Resultado r = Formulario.validar(CRIACAO, Map.of("data_nascimento", "31/02/1990", "cpf", "123",
                "email_superior", "sem-arroba", "usuario_estagiario", "talvez"));
        assertEquals(Map.of("nome_completo", "Obrigatório", "data_nascimento", "Data inválida (use dd/mm/aaaa)",
                "cpf", "Formato inválido", "email_superior", "E-mail inválido", "usuario_estagiario", "Opção inválida"), r.erros());
    }

    @Test
    void textoLongoDemais() {
        Resultado r = Formulario.validar(CRIACAO, Map.of("nome_completo", "x".repeat(501)));
        assertTrue(r.erros().get("nome_completo").startsWith("Texto longo demais"));
    }

    @Test
    void definicaoInvalida() {
        Definicao d = new Definicao(List.of(new Secao("S", List.of(
                new Pergunta("Nome Errado", "x", "text", false, null, null),
                new Pergunta("a", "", "cor", false, null, null),
                new Pergunta("b", "B", "select", false, List.of(), null),
                new Pergunta("c", "C", "regex", false, null, "/([/"),
                new Pergunta("c", "C2", "text", false, null, null)))));
        assertEquals(6, Formulario.validarDefinicao(d).size(), Formulario.validarDefinicao(d).toString());
        assertFalse(Formulario.validarDefinicao(new Definicao(List.of())).isEmpty());
    }

    @Test
    void tituloEDescricaoSemInjecaoDeHtml() {
        Map<String, String> resp = Map.of("nome_completo", "<script>x</script> Ana", "cpf", "12345678901");
        assertEquals("Criação de usuário: <script>x</script> Ana", Formulario.titulo("Criação de usuário: {nome_completo}", "p", resp));
        assertEquals("Padrão", Formulario.titulo(null, "Padrão", resp));
        String html = Formulario.descricaoHtml(CRIACAO, resp);
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("Acessos"), "seção sem resposta não aparece");
    }

    @Test
    void regexEstiloJavascriptComFlag() {
        assertNotNull(Formulario.compilar("/^ana$/i"));
        assertTrue(Formulario.compilar("/^ana$/i").matcher("ANA").find());
        assertTrue(Formulario.compilar("^[0-9]+$").matcher("123").find());
        assertNull(Formulario.compilar("/([/"));
    }

}
