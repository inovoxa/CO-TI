package cn.iocoder.yudao.module.itsm.dominio;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Motor de regras de chamado (GLPI: RuleTicket / RuleCollection::processAllRules), sem banco.
 *
 * <p>As regras são avaliadas em ordem. Uma regra "casa" quando todos (E) ou algum (OU) dos critérios são
 * verdadeiros; suas ações entram na saída na ordem. Com "parar", as regras seguintes não são avaliadas
 * (GLPI: stop_on_first_match por regra). Textos são comparados sem diferenciar maiúsculas e acentos.
 */
public final class MotorRegras {

    /** Campos que um critério pode usar (fatos do chamado). */
    public static final Set<String> CAMPOS = Set.of("titulo", "descricao", "categoria_id", "tipo", "urgencia", "impacto",
            "prioridade", "origem_id", "dept_id", "solicitante_id", "formulario_id");
    public static final Set<String> OPERADORES = Set.of("igual", "diferente", "contem", "nao_contem", "comeca", "regex",
            "em", "maior", "menor");
    /** Ações suportadas pelo serviço que aplica o resultado. */
    public static final Set<String> ACOES = Set.of("atribuir_equipe", "atribuir_tecnico", "adicionar_observador",
            "definir_categoria", "definir_urgencia", "definir_impacto", "definir_prioridade", "definir_tipo",
            "pedir_aprovacao_gestor", "pedir_aprovacao_usuario");

    /** Texto avaliado por regex é cortado aqui: evita expressão cara sobre descrições enormes. */
    private static final int LIMITE_TEXTO_REGEX = 5_000;

    public record Criterio(String campo, String operador, String valor) {
    }

    public record Acao(String acao, String valor) {
    }

    public record Regra(Long id, String nome, boolean todos, boolean parar, List<Criterio> criterios, List<Acao> acoes) {
    }

    public record Disparo(Long regraId, String regraNome, Acao acao) {
    }

    private MotorRegras() {
    }

    public static List<Disparo> avaliar(List<Regra> regras, Map<String, Object> fatos) {
        List<Disparo> saida = new ArrayList<>();
        for (Regra r : regras) {
            if (r.criterios().isEmpty()) {
                continue; // regra sem critério nunca casa (evita "vale para tudo" por engano)
            }
            boolean casou = r.todos()
                    ? r.criterios().stream().allMatch(c -> verdadeiro(c, fatos))
                    : r.criterios().stream().anyMatch(c -> verdadeiro(c, fatos));
            if (casou) {
                r.acoes().forEach(a -> saida.add(new Disparo(r.id(), r.nome(), a)));
                if (r.parar()) {
                    break;
                }
            }
        }
        return saida;
    }

    static boolean verdadeiro(Criterio c, Map<String, Object> fatos) {
        Object bruto = fatos.get(c.campo());
        String fato = bruto == null ? "" : String.valueOf(bruto);
        String valor = c.valor() == null ? "" : c.valor();
        return switch (c.operador()) {
            case "igual" -> normal(fato).equals(normal(valor));
            case "diferente" -> !normal(fato).equals(normal(valor));
            case "contem" -> !valor.isEmpty() && normal(fato).contains(normal(valor));
            case "nao_contem" -> valor.isEmpty() || !normal(fato).contains(normal(valor));
            case "comeca" -> normal(fato).startsWith(normal(valor));
            case "em" -> Arrays.stream(valor.split(",")).map(MotorRegras::normal).anyMatch(normal(fato)::equals);
            case "maior" -> numero(fato) != null && numero(valor) != null && numero(fato) > numero(valor);
            case "menor" -> numero(fato) != null && numero(valor) != null && numero(fato) < numero(valor);
            case "regex" -> regex(valor, fato);
            default -> false;
        };
    }

    private static boolean regex(String expressao, String texto) {
        try {
            String t = texto.length() > LIMITE_TEXTO_REGEX ? texto.substring(0, LIMITE_TEXTO_REGEX) : texto;
            return Pattern.compile(expressao, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(t).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static Double numero(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String normal(String s) {
        String sem = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sem.toLowerCase(Locale.ROOT);
    }

    /** Validação do cadastro: campos, operadores, ações e regex válidos. */
    public static List<String> validar(List<Criterio> criterios, List<Acao> acoes) {
        List<String> erros = new ArrayList<>();
        if (criterios.isEmpty()) {
            erros.add("A regra precisa de pelo menos um critério");
        }
        if (acoes.isEmpty()) {
            erros.add("A regra precisa de pelo menos uma ação");
        }
        for (Criterio c : criterios) {
            if (!CAMPOS.contains(c.campo())) {
                erros.add("Campo desconhecido: " + c.campo());
            }
            if (!OPERADORES.contains(c.operador())) {
                erros.add("Operador desconhecido: " + c.operador());
            }
            if ("regex".equals(c.operador())) {
                try {
                    Pattern.compile(c.valor());
                } catch (PatternSyntaxException | NullPointerException e) {
                    erros.add("Expressão regular inválida: " + c.valor());
                }
            }
        }
        for (Acao a : acoes) {
            if (!ACOES.contains(a.acao())) {
                erros.add("Ação desconhecida: " + a.acao());
            }
        }
        return erros;
    }

}
