package cn.iocoder.yudao.module.atendimento.dominio;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Supplier;

/**
 * Regras de automação do atendimento (evento → condições → ações) e as ações das macros, sem dependência de servidor:
 * o que existe, como cada condição é avaliada e a validação do que a tela manda. Ver docs/specs/atendimento-automacoes.md.
 */
public final class Automacoes {

    private Automacoes() {
    }

    public static final List<String> EVENTOS = List.of("conversa_criada", "mensagem_recebida", "conversa_reaberta", "conversa_resolvida");

    /** Campo da condição → operadores aceitos */
    public static final Map<String, List<String>> CAMPOS = Map.ofEntries(
            Map.entry("caixa", List.of("em", "fora_de")),
            Map.entry("canal", List.of("em", "fora_de")),
            Map.entry("conteudo", List.of("contem", "nao_contem", "comeca_com")),
            Map.entry("status", List.of("em", "fora_de")),
            Map.entry("prioridade", List.of("em", "fora_de", "vazio", "nao_vazio")),
            Map.entry("etiqueta", List.of("tem", "nao_tem")),
            Map.entry("atendente", List.of("em", "fora_de", "vazio", "nao_vazio")),
            Map.entry("contato_email", List.of("contem", "nao_contem", "vazio", "nao_vazio")),
            Map.entry("contato_telefone", List.of("comeca_com", "vazio", "nao_vazio")),
            Map.entry("contato_portal", List.of("sim", "nao")),
            Map.entry("horario", List.of("dentro", "fora")));

    public static final List<String> ACOES = List.of("atribuir_agente", "atribuir_automatico", "remover_agente", "adicionar_etiquetas",
            "remover_etiquetas", "enviar_mensagem", "nota", "status", "prioridade");

    /** Uma condição: campo, operador e valor (lista de ids/códigos ou texto, conforme o campo). */
    public record Condicao(String campo, String operador, Object valor) {
    }

    /** Uma ação: tipo e valor (id, lista de ids, texto, código). */
    public record Acao(String tipo, Object valor) {
    }

    /**
     * O que a regra pode consultar sobre a conversa. {@code emExpediente} só é calculado se alguma condição perguntar
     * (precisa do calendário da caixa).
     */
    public record Contexto(Long caixaId, String canal, int status, Integer prioridade, Long agenteId, Set<Long> etiquetas,
                           String conteudo, String contatoEmail, String contatoTelefone, boolean contatoPortal,
                           Supplier<Boolean> emExpediente) {
    }

    /** Todas as condições valem (E). Lista vazia = sempre. */
    public static boolean atende(List<Condicao> condicoes, Contexto c) {
        for (Condicao x : condicoes) {
            if (!avaliar(x, c)) {
                return false;
            }
        }
        return true;
    }

    static boolean avaliar(Condicao x, Contexto c) {
        String op = x.operador();
        return switch (x.campo()) {
            case "caixa" -> pertence(op, ids(x.valor()).contains(c.caixaId()));
            case "canal" -> pertence(op, textos(x.valor()).contains(c.canal()));
            case "status" -> pertence(op, ids(x.valor()).contains((long) c.status()));
            case "prioridade" -> switch (op) {
                case "vazio" -> c.prioridade() == null;
                case "nao_vazio" -> c.prioridade() != null;
                default -> pertence(op, c.prioridade() != null && ids(x.valor()).contains((long) c.prioridade()));
            };
            case "atendente" -> switch (op) {
                case "vazio" -> c.agenteId() == null;
                case "nao_vazio" -> c.agenteId() != null;
                default -> pertence(op, c.agenteId() != null && ids(x.valor()).contains(c.agenteId()));
            };
            case "etiqueta" -> {
                boolean tem = ids(x.valor()).stream().anyMatch(c.etiquetas()::contains);
                yield "tem".equals(op) == tem;
            }
            case "conteudo" -> texto(op, c.conteudo(), x.valor());
            case "contato_email" -> texto(op, c.contatoEmail(), x.valor());
            case "contato_telefone" -> texto(op, c.contatoTelefone() == null ? null : c.contatoTelefone().replaceAll("\\D", ""),
                    x.valor() == null ? null : x.valor().toString().replaceAll("\\D", ""));
            case "contato_portal" -> "sim".equals(op) == c.contatoPortal();
            case "horario" -> "dentro".equals(op) == Boolean.TRUE.equals(c.emExpediente().get());
            default -> false;
        };
    }

    private static boolean pertence(String op, boolean dentro) {
        return "fora_de".equals(op) != dentro;
    }

    /**
     * Texto sem diferença de maiúsculas e acentos. Para {@code contem} e {@code nao_contem}, o valor pode ter várias
     * palavras separadas por vírgula: basta uma.
     */
    private static boolean texto(String op, String atual, Object valor) {
        String a = simples(atual);
        if ("vazio".equals(op)) {
            return a.isEmpty();
        }
        if ("nao_vazio".equals(op)) {
            return !a.isEmpty();
        }
        List<String> termos = Arrays.stream(simples(valor == null ? "" : valor.toString()).split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
        if (termos.isEmpty()) {
            return false;
        }
        return switch (op) {
            case "contem" -> termos.stream().anyMatch(a::contains);
            case "nao_contem" -> termos.stream().noneMatch(a::contains);
            case "comeca_com" -> termos.stream().anyMatch(a::startsWith);
            default -> false;
        };
    }

    public static String simples(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    public static List<Long> ids(Object valor) {
        List<Long> out = new ArrayList<>();
        if (valor instanceof Collection<?> c) {
            for (Object o : c) {
                Long n = id(o);
                if (n != null) {
                    out.add(n);
                }
            }
        } else {
            Long n = id(valor);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    public static Long id(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return o == null || o.toString().isBlank() ? null : Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> textos(Object valor) {
        if (valor instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).toList();
        }
        return valor == null ? List.of() : List.of(valor.toString());
    }

    /** Troca as variáveis do texto da ação: {{contato.nome}}, {{conversa.id}}, {{atendente.nome}}. */
    public static String preencher(String texto, Map<String, String> variaveis) {
        String t = texto == null ? "" : texto;
        for (Map.Entry<String, String> v : variaveis.entrySet()) {
            t = t.replaceAll("\\{\\{\\s*" + java.util.regex.Pattern.quote(v.getKey()) + "\\s*}}",
                    java.util.regex.Matcher.quoteReplacement(v.getValue() == null ? "" : v.getValue()));
        }
        return t;
    }

    // ================================================================================== validação

    /** @return o problema, ou null se a condição é válida */
    public static String problemaCondicao(Condicao c) {
        List<String> ops = CAMPOS.get(c.campo());
        if (ops == null) {
            return "condição desconhecida: " + c.campo();
        }
        if (!ops.contains(c.operador())) {
            return "operador \"" + c.operador() + "\" não serve para " + c.campo();
        }
        boolean semValor = Set.of("vazio", "nao_vazio", "sim", "nao", "dentro", "fora").contains(c.operador());
        if (semValor) {
            return null;
        }
        return switch (c.campo()) {
            case "conteudo", "contato_email", "contato_telefone" ->
                    c.valor() == null || c.valor().toString().isBlank() || c.valor().toString().length() > 500 ? "informe o texto (até 500 caracteres)" : null;
            case "canal" -> textos(c.valor()).isEmpty() ? "escolha ao menos um canal" : null;
            default -> ids(c.valor()).isEmpty() ? "escolha ao menos um valor para " + c.campo() : null;
        };
    }

    /** @return o problema, ou null se a ação é válida */
    public static String problemaAcao(Acao a) {
        if (!ACOES.contains(a.tipo())) {
            return "ação desconhecida: " + a.tipo();
        }
        return switch (a.tipo()) {
            case "atribuir_agente" -> id(a.valor()) == null ? "escolha o atendente" : null;
            case "adicionar_etiquetas", "remover_etiquetas" -> ids(a.valor()).isEmpty() ? "escolha as etiquetas" : null;
            case "enviar_mensagem", "nota" -> a.valor() == null || a.valor().toString().isBlank() || a.valor().toString().length() > 4096
                    ? "escreva o texto (até 4096 caracteres)" : null;
            case "status" -> List.of("aberta", "pendente", "resolvida").contains(String.valueOf(a.valor())) ? null : "status inválido";
            case "prioridade" -> {
                Long p = id(a.valor());
                yield p == null || p < 0 || p > 4 ? "prioridade de 0 (nenhuma) a 4 (urgente)" : null;
            }
            default -> null;
        };
    }

}
