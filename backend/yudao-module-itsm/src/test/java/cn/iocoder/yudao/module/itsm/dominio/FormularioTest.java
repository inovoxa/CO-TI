package cn.iocoder.yudao.module.itsm.dominio;

import cn.iocoder.yudao.module.itsm.dominio.Formulario.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FormularioTest {

    private static final Definicao CONDICIONAL = new Definicao(List.of(new Secao("s", List.of(
            new Pergunta("sistemas", "Sistemas", "multipla", true, List.of("A", "B", "C"), null),
            new Pergunta("detalhe_a", "Detalhe de A", "text", true, null, null, null,
                    List.of(new Condicao("sistemas", List.of("A")))),
            new Pergunta("colega", "Tem colega?", "radios", false, List.of("Sim", "Não"), null, null,
                    List.of(new Condicao("sistemas", List.of("B")))),
            new Pergunta("login", "Login", "text", true, null, null, null,
                    List.of(new Condicao("colega", List.of("Sim"))))))));

    @Test
    void perguntaOcultaNaoEObrigatoriaEARespostaEDescartada() {
        Resultado r = Formulario.validar(CONDICIONAL, Map.of("sistemas", List.of("C"), "detalhe_a", "enviado à força"));
        assertTrue(r.valido());
        assertEquals(Map.of("sistemas", "C"), r.respostas());
    }

    @Test
    void perguntaVisivelContinuaObrigatoria() {
        assertEquals(Set.of("detalhe_a"), Formulario.validar(CONDICIONAL, Map.of("sistemas", List.of("A", "C"))).erros().keySet());
    }

    @Test
    void ocultarAPerguntaDeOrigemOcultaAsQueDependemDela() {
        Resultado r = Formulario.validar(CONDICIONAL, Map.of("sistemas", List.of("C"), "colega", "Sim"));
        assertTrue(r.valido());
        assertFalse(r.respostas().containsKey("colega"));
    }

    @Test
    void multiplaGravaSeparadoERecusaOpcaoForaDaLista() {
        assertEquals("A; B", Formulario.validar(CONDICIONAL, Map.of("sistemas", List.of("A", "B"), "detalhe_a", "x"))
                .respostas().get("sistemas"));
        assertEquals(Set.of("sistemas"), Formulario.validar(CONDICIONAL, Map.of("sistemas", List.of("Z"))).erros().keySet());
    }

    @Test
    void multiplaVaziaNaoPassaComoObrigatoria() {
        assertEquals(Set.of("sistemas"), Formulario.validar(CONDICIONAL, Map.of("sistemas", List.of())).erros().keySet());
    }

    @Test
    void condicaoSoPodeDependerDePerguntaAnterior() {
        Definicao d = new Definicao(List.of(new Secao("s", List.of(
                new Pergunta("b", "B", "text", false, null, null, null, List.of(new Condicao("a", List.of("x")))),
                new Pergunta("a", "A", "text", false, null, null)))));
        assertFalse(Formulario.validarDefinicao(d).isEmpty());
    }

    @Test
    void soFormularioComCondicaoOuMultiplaFicaForaDoWhatsApp() {
        assertTrue(CONDICIONAL.perguntas().get(0).soNoPortal());
        assertTrue(CONDICIONAL.perguntas().get(1).soNoPortal());
        assertFalse(new Pergunta("a", "A", "radios", true, List.of("x"), null).soNoPortal());
    }

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

    @Test
    void usuarioDeRedeConfereSoOFormato() {
        Definicao d = new Definicao(List.of(new Secao("Acesso", List.of(
                new Pergunta("perfil_referencia", "Colega com o perfil", "usuario", true, null, null)))));
        assertTrue(Formulario.validarDefinicao(d).isEmpty());
        for (String ok : List.of("joao.silva", "PMA\\joao.silva", "joao.silva@pma.local", "jose-maria_2")) {
            assertTrue(Formulario.validar(d, Map.of("perfil_referencia", ok)).valido(), ok);
        }
        for (String ruim : List.of("joao silva", "*)(cn=*", "a;b", "a\\b\\c")) {
            assertEquals("Usuário de rede inválido", Formulario.validar(d, Map.of("perfil_referencia", ruim)).erros()
                    .get("perfil_referencia"), ruim);
        }
    }

}
