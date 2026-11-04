package cn.iocoder.yudao.module.itsm.dominio;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Definição e validação de formulário do catálogo (GLPI: Glpi\Form — seções, perguntas, validação), sem banco.
 *
 * <p>Formato compatível com o formularios_definicoes.json do projeto anterior: tipos text, textarea, email,
 * number, date, select, radios, regex e location; "regex" no estilo JavaScript (/expressão/flags).
 *
 * <p>Tipo do CO-TI: "usuario", o login de rede de uma pessoa (no portal, lista com busca). Aqui só se confere o
 * formato; se a conta existe, quem confere é o serviço, que tem o banco.
 *
 * <p>Tipo do CO-TI: "multipla", várias opções, gravadas separadas por "; ". Uma pergunta com "visivelSe" só vale quando
 * as condições casam: oculta, não é obrigatória e a resposta é descartada, inclusive se vier no envio.
 */
public final class Formulario {

    public static final Set<String> TIPOS = Set.of("text", "textarea", "email", "number", "date", "select", "radios",
            "regex", "location", "usuario", "multipla");
    private static final String SEPARADOR = "; ";
    private static final Pattern NOME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    /** sAMAccountName (até 20 no AD, folga para UPN) ou DOMINIO\login / login@dominio, que o serviço reduz ao login. */
    private static final Pattern LOGIN = Pattern.compile("^[\\p{L}0-9._\\-]+([\\\\@][\\p{L}0-9._\\-]+)?$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,}$");
    private static final Pattern REGEX_JS = Pattern.compile("^/(.*)/([a-z]*)$", Pattern.DOTALL);
    // STRICT: 31/02 é erro (o padrão do Java "ajustaria" para 28/02)
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final int MAX_TEXTO = 500;
    private static final int MAX_TEXTO_LONGO = 10_000;

    /** Casa quando a resposta de "campo", pergunta anterior do formulário, tem um dos "valores". */
    public record Condicao(String campo, List<String> valores) {
    }

    /**
     * @param ajuda     texto de apoio abaixo da pergunta
     * @param visivelSe condições (todas precisam casar); vazio = sempre visível
     */
    public record Pergunta(String name, String label, String type, boolean required, List<String> values, String regex,
                           String ajuda, List<Condicao> visivelSe) {

        public Pergunta(String name, String label, String type, boolean required, List<String> values, String regex) {
            this(name, label, type, required, values, regex, null, null);
        }

        /** O fluxo do WhatsApp pergunta uma coisa de cada vez e não trata condição nem múltipla escolha. */
        public boolean soNoPortal() {
            return "multipla".equals(type) || (visivelSe != null && !visivelSe.isEmpty());
        }
    }

    public record Secao(String nome, List<Pergunta> perguntas) {
    }

    public record Definicao(List<Secao> secoes) {
        public List<Pergunta> perguntas() {
            return secoes.stream().flatMap(s -> s.perguntas().stream()).toList();
        }
    }

    public record Resultado(Map<String, String> respostas, Map<String, String> erros) {
        public boolean valido() {
            return erros.isEmpty();
        }
    }

    private Formulario() {
    }

    /** Erros na própria definição (feita pelo administrador ou importada). */
    public static List<String> validarDefinicao(Definicao d) {
        List<String> erros = new ArrayList<>();
        if (d == null || d.secoes() == null || d.secoes().isEmpty()) {
            return List.of("O formulário precisa de pelo menos uma seção");
        }
        Set<String> nomes = new HashSet<>();
        Set<String> anteriores = new HashSet<>();
        for (Pergunta p : d.perguntas()) {
            if (p.name() == null || !NOME.matcher(p.name()).matches()) {
                erros.add("Nome de pergunta inválido (use minúsculas, números e _): " + p.name());
            } else if (!nomes.add(p.name())) {
                erros.add("Pergunta repetida: " + p.name());
            }
            if (p.label() == null || p.label().isBlank()) {
                erros.add("Pergunta sem texto: " + p.name());
            }
            if (!TIPOS.contains(p.type())) {
                erros.add("Tipo desconhecido na pergunta " + p.name() + ": " + p.type());
            }
            if (("select".equals(p.type()) || "radios".equals(p.type()) || "multipla".equals(p.type()))
                    && (p.values() == null || p.values().isEmpty())) {
                erros.add("A pergunta " + p.name() + " precisa de opções");
            }
            if ("multipla".equals(p.type()) && p.values() != null && p.values().stream().anyMatch(v -> v.contains(";"))) {
                erros.add("As opções da pergunta " + p.name() + " não podem ter ponto e vírgula");
            }
            if (p.visivelSe() != null) {
                for (Condicao c : p.visivelSe()) {
                    // só pergunta anterior: assim não há ciclo e a visibilidade se resolve em uma passada
                    if (c == null || c.campo() == null || !anteriores.contains(c.campo())) {
                        erros.add("A pergunta " + p.name() + " depende de uma pergunta que não vem antes dela");
                    } else if (c.valores() == null || c.valores().isEmpty()) {
                        erros.add("A condição da pergunta " + p.name() + " precisa de valores");
                    }
                }
            }
            anteriores.add(p.name());
            if ("regex".equals(p.type()) && compilar(p.regex()) == null) {
                erros.add("Expressão regular inválida na pergunta " + p.name());
            }
        }
        if (d.perguntas().isEmpty()) {
            erros.add("O formulário precisa de pelo menos uma pergunta");
        }
        return erros;
    }

    /** Valida e normaliza as respostas; perguntas que não existem no formulário são descartadas. */
    public static Resultado validar(Definicao d, Map<String, ?> entrada) {
        Map<String, String> ok = new LinkedHashMap<>();
        Map<String, String> erros = new LinkedHashMap<>();
        for (Pergunta p : d.perguntas()) {
            if (!visivel(p, ok)) {
                continue;
            }
            String v = texto(entrada == null ? null : entrada.get(p.name()));
            if (v.isEmpty()) {
                if (p.required()) {
                    erros.put(p.name(), "Obrigatório");
                }
                continue;
            }
            String erro = erro(p, v);
            if (erro != null) {
                erros.put(p.name(), erro);
            } else {
                ok.put(p.name(), normalizar(p, v));
            }
        }
        return new Resultado(ok, erros);
    }

    /** Lista (vinda da tela) vira texto separado, sem itens vazios. */
    private static String texto(Object bruto) {
        if (bruto instanceof Collection<?> lista) {
            return lista.stream().filter(Objects::nonNull).map(x -> String.valueOf(x).trim()).filter(x -> !x.isEmpty())
                    .distinct().collect(java.util.stream.Collectors.joining(SEPARADOR));
        }
        return bruto == null ? "" : String.valueOf(bruto).trim();
    }

    /** As respostas já validadas das perguntas anteriores decidem; pergunta oculta não tem resposta, e a cadeia cai. */
    private static boolean visivel(Pergunta p, Map<String, String> respostas) {
        if (p.visivelSe() == null || p.visivelSe().isEmpty()) {
            return true;
        }
        return p.visivelSe().stream().allMatch(c -> {
            String r = respostas.get(c.campo());
            return r != null && c.valores() != null && Arrays.stream(r.split(SEPARADOR)).anyMatch(c.valores()::contains);
        });
    }

    private static String erro(Pergunta p, String v) {
        int max = "textarea".equals(p.type()) || "multipla".equals(p.type()) ? MAX_TEXTO_LONGO : MAX_TEXTO;
        if (v.length() > max) {
            return "Texto longo demais (máximo " + max + " caracteres)";
        }
        return switch (p.type()) {
            case "email" -> EMAIL.matcher(v).matches() ? null : "E-mail inválido";
            case "number" -> v.matches("^-?\\d{1,15}([.,]\\d{1,6})?$") ? null : "Número inválido";
            case "date" -> data(v) == null ? "Data inválida (use dd/mm/aaaa)" : null;
            case "select", "radios" -> p.values().contains(v) ? null : "Opção inválida";
            case "multipla" -> p.values().containsAll(Arrays.asList(v.split(SEPARADOR))) ? null : "Opção inválida";
            case "usuario" -> v.length() <= 104 && LOGIN.matcher(v).matches() ? null : "Usuário de rede inválido";
            case "regex" -> {
                Pattern r = compilar(p.regex());
                yield r != null && r.matcher(v).find() ? null : "Formato inválido";
            }
            default -> null;
        };
    }

    private static String normalizar(Pergunta p, String v) {
        if ("date".equals(p.type())) {
            return BR.format(data(v));
        }
        if ("email".equals(p.type())) {
            return v.toLowerCase(Locale.ROOT);
        }
        return v;
    }

    private static LocalDate data(String v) {
        try {
            return v.contains("/") ? LocalDate.parse(v, BR) : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "/^[0-9]{11}$/" (estilo JavaScript) ou a expressão pura. */
    static Pattern compilar(String regex) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        try {
            Matcher m = REGEX_JS.matcher(regex.trim());
            if (m.matches()) {
                int flags = m.group(2).contains("i") ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
                return Pattern.compile(m.group(1), flags);
            }
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /** Troca {nome_da_pergunta} pelas respostas (sem HTML). */
    public static String titulo(String modelo, String padrao, Map<String, String> respostas) {
        if (modelo == null || modelo.isBlank()) {
            return padrao;
        }
        Matcher m = Pattern.compile("\\{([a-z][a-z0-9_]*)}").matcher(modelo);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(respostas.getOrDefault(m.group(1), "")));
        }
        m.appendTail(sb);
        String t = sb.toString().trim();
        return t.length() > 255 ? t.substring(0, 255) : t;
    }

    /** Descrição do chamado: tabela "pergunta / resposta", com tudo escapado. */
    public static String descricaoHtml(Definicao d, Map<String, String> respostas) {
        StringBuilder sb = new StringBuilder();
        for (Secao s : d.secoes()) {
            List<Pergunta> respondidas = s.perguntas().stream().filter(p -> respostas.containsKey(p.name())).toList();
            if (respondidas.isEmpty()) {
                continue;
            }
            sb.append("<p><b>").append(escapar(s.nome())).append("</b></p><table><tbody>");
            for (Pergunta p : respondidas) {
                sb.append("<tr><td>").append(escapar(p.label())).append("</td><td>")
                        .append(escapar(respostas.get(p.name()))).append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }
        return sb.toString();
    }

    /** Escapa texto para entrar em HTML. */
    public static String escaparHtml(String s) {
        return escapar(s);
    }

    static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
