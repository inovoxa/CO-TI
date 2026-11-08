package cn.iocoder.yudao.module.atendimento.dominio;

import cn.iocoder.yudao.module.itsm.dominio.Formulario;

import java.util.*;

/**
 * Textos e interpretação das respostas do fluxo do catálogo pelo WhatsApp (docs/specs/atendimento-fluxo-whatsapp.md),
 * sem dependência de servidor. A validação dos campos é a mesma do portal ({@link Formulario#validar}).
 */
public final class FluxoTextos {

    private FluxoTextos() {
    }

    /** Campos com dado pessoal: aparecem mascarados no resumo e na conversa depois do envio */
    public static final Set<String> SENSIVEIS = Set.of("cpf", "data_nascimento", "email_pessoal", "matricula", "celular", "celular_contato");

    public static final String MENU = """
            1️⃣ Abrir um chamado
            2️⃣ Consultar meus chamados
            3️⃣ Tirar uma dúvida (base de conhecimento)
            4️⃣ Falar com um atendente

            Responda com o número da opção ou descreva o que precisa.""";

    public static final String AJUDA_COMANDOS = "A qualquer momento: *menu* volta ao início, *voltar* refaz a pergunta anterior, *cancelar* descarta o pedido e *atendente* chama uma pessoa.";

    public enum Comando { MENU, VOLTAR, CANCELAR, ATENDENTE, SENHA }

    /** Comando global, se a mensagem for um. */
    public static Comando comando(String texto) {
        String t = Automacoes.simples(texto).replaceAll("[.!]+$", "");
        return switch (t) {
            case "menu", "0", "inicio" -> Comando.MENU;
            case "voltar" -> Comando.VOLTAR;
            case "cancelar", "sair" -> Comando.CANCELAR;
            case "atendente", "humano", "falar com atendente", "falar com um atendente", "pessoa" -> Comando.ATENDENTE;
            // pedido da senha provisória de um reset/criação já executado no AD
            case "senha", "senha provisoria", "minha senha", "ver senha", "receber senha", "enviar senha" -> Comando.SENHA;
            default -> null;
        };
    }

    /** "sim", "1", "s", "ok", "confirmo" → true; "não", "2", "n" → false; outra coisa → null. */
    public static Boolean simNao(String texto) {
        String t = Automacoes.simples(texto).replaceAll("[.!]+$", "");
        if (Set.of("1", "sim", "s", "ok", "confirmo", "pode", "isso", "confirmar").contains(t)) {
            return true;
        }
        if (Set.of("2", "nao", "n", "nao quero").contains(t)) {
            return false;
        }
        return null;
    }

    /** Número inteiro digitado (só o número), ou null. */
    public static Integer numero(String texto) {
        String t = texto == null ? "" : texto.strip().replaceAll("[.)ºª]+$", "");
        return t.matches("\\d{1,4}") ? Integer.parseInt(t) : null;
    }

    /** Código de serviço do catálogo ("2.1", "2.11"), se a mensagem for só isso. */
    /** Serviços que só o portal atende: o chat não trata perguntas condicionais nem múltipla escolha. */
    public static String soNoPortal(List<String> servicos, String urlPublica) {
        if (servicos.isEmpty()) {
            return "";
        }
        String base = urlPublica == null ? "" : urlPublica.trim().replaceAll("/+$", "");
        return "Pelo portal" + (base.isEmpty() ? " (Catálogo de serviços)" : ": " + base + "/catalogo") + "\n"
                + servicos.stream().map(s -> "- " + s).collect(java.util.stream.Collectors.joining("\n"));
    }

    public static String codigo(String texto) {
        String t = texto == null ? "" : texto.strip();
        return t.matches("\\d{1,2}\\.\\d{1,2}") ? t : null;
    }

    /** Texto da pergunta, com a posição, o formato esperado e as opções numeradas. */
    public static String pergunta(Formulario.Pergunta p, int posicao, int total) {
        StringBuilder s = new StringBuilder("*").append(posicao).append("/").append(total).append("* ").append(p.label().strip());
        switch (p.type()) {
            case "date" -> {
                if (!p.label().contains("dd/mm")) {
                    s.append("\n_(dd/mm/aaaa)_");
                }
            }
            case "email" -> s.append("\n_(e-mail)_");
            case "usuario" -> s.append("\n_(usuário de rede, ex.: nome.sobrenome)_");
            case "number" -> s.append("\n_(só números)_");
            case "select", "radios" -> {
                for (int i = 0; i < p.values().size(); i++) {
                    s.append("\n").append(i + 1).append(") ").append(p.values().get(i));
                }
            }
            default -> {
            }
        }
        if (!p.required()) {
            s.append("\n_(opcional: envie - para pular)_");
        }
        return s.toString();
    }

    /**
     * A resposta do contato no formato do formulário: para opções, aceita o número ou o texto da opção.
     *
     * @return o valor a validar (null = pulou uma pergunta opcional)
     */
    public static String resposta(Formulario.Pergunta p, String texto) {
        String t = texto == null ? "" : texto.strip();
        if (!p.required() && ("-".equals(t) || "pular".equalsIgnoreCase(t))) {
            return null;
        }
        if ("select".equals(p.type()) || "radios".equals(p.type())) {
            Integer n = numero(t);
            if (n != null && n >= 1 && n <= p.values().size()) {
                return p.values().get(n - 1);
            }
            String s = Automacoes.simples(t);
            for (String v : p.values()) {
                if (Automacoes.simples(v).equals(s)) {
                    return v;
                }
            }
        }
        return t;
    }

    /** Erro de uma resposta isolada, pelas mesmas regras do portal. Null = válida. */
    public static String erro(Formulario.Pergunta p, String valor) {
        Formulario.Definicao d = new Formulario.Definicao(List.of(new Formulario.Secao("x",
                List.of(new Formulario.Pergunta(p.name(), p.label(), p.type(), true, p.values(), p.regex())))));
        Formulario.Resultado r = Formulario.validar(d, Map.of(p.name(), valor));
        return r.valido() ? null : r.erros().get(p.name());
    }

    /**
     * Pelo celular, CPF, CEP e telefone chegam com pontuação ("14801-000", "123.456.789-01"). Se a pergunta valida por
     * expressão e o valor só com os dígitos passa, vale o valor só com os dígitos.
     */
    public static String ajustar(Formulario.Pergunta p, String valor) {
        if (valor == null || !"regex".equals(p.type()) || erro(p, valor) == null) {
            return valor;
        }
        String digitos = valor.replaceAll("[\\s.\\-()/]", "");
        return !digitos.equals(valor) && erro(p, digitos) == null ? digitos : valor;
    }

    /** Valor normalizado (datas, e-mail), como o portal grava. */
    public static String normalizado(Formulario.Pergunta p, String valor) {
        Formulario.Definicao d = new Formulario.Definicao(List.of(new Formulario.Secao("x",
                List.of(new Formulario.Pergunta(p.name(), p.label(), p.type(), true, p.values(), p.regex())))));
        return Formulario.validar(d, Map.of(p.name(), valor)).respostas().getOrDefault(p.name(), valor);
    }

    /** Dado pessoal: só os 3 últimos caracteres aparecem. */
    public static String mascarar(String nome, String valor) {
        if (valor == null || !SENSIVEIS.contains(nome)) {
            return valor;
        }
        String v = valor.strip();
        return v.length() <= 3 ? "***" : "•".repeat(Math.min(8, v.length() - 3)) + v.substring(v.length() - 3);
    }

    /** Resumo numerado para confirmar (dados pessoais mascarados). */
    public static String resumo(String nomeServico, List<Formulario.Pergunta> perguntas, Map<String, String> respostas) {
        StringBuilder s = new StringBuilder("*Confira o pedido: ").append(nomeServico).append("*\n");
        int i = 1;
        for (Formulario.Pergunta p : perguntas) {
            String v = respostas.get(p.name());
            s.append("\n").append(i++).append(". ").append(p.label().strip()).append(": ")
                    .append(v == null || v.isBlank() ? "—" : mascarar(p.name(), v));
        }
        s.append("\n\n1️⃣ Confirmar e abrir o chamado\n2️⃣ Cancelar\n3️⃣ Corrigir um campo");
        return s.toString();
    }

}
