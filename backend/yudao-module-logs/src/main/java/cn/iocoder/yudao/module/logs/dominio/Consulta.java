package cn.iocoder.yudao.module.logs.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Linguagem de busca no estilo do Graylog, traduzida para SQL parametrizado:
 * <pre>
 *   texto livre             mensagem contém (sem diferenciar maiúsculas)
 *   campo:valor             igual (campos do FortiGate/Windows ou colunas: origem, fluxo, aplicacao, ip, nivel)
 *   campo:"valor com espaço"
 *   campo:abc*  campo:*$    curingas
 *   campo:&lt;=3  campo:&gt;100  comparação numérica
 *   _exists_:campo          o campo existe
 *   a AND b, a OR b, NOT a, -a, (a OR b) AND c   (espaço entre termos = AND)
 * </pre>
 */
public final class Consulta {

    public record Sql(String condicao, List<Object> parametros) {
    }

    /** Colunas próprias da tabela (o resto vai para o JSON de campos). Apelidos em inglês, como no Graylog. */
    private static final Map<String, String> COLUNAS = Map.of("origem", "origem", "source", "origem",
            "fluxo", "fluxo", "aplicacao", "aplicacao", "ip", "ip", "mensagem", "mensagem", "message", "mensagem",
            "nivel", "nivel", "level", "nivel");
    private static final Pattern NOME_CAMPO = Pattern.compile("[A-Za-z_][\\w.-]{0,99}");
    private static final Pattern NUMERO = Pattern.compile("-?\\d+(\\.\\d+)?");

    private final List<String> tokens;
    private final List<Object> parametros = new ArrayList<>();
    private int pos;

    private Consulta(List<String> tokens) {
        this.tokens = tokens;
    }

    /** @throws IllegalArgumentException com a explicação, se a consulta estiver mal escrita */
    public static Sql traduzir(String texto) {
        if (texto == null || texto.isBlank()) {
            return new Sql("TRUE", List.of());
        }
        if (texto.length() > 2000) {
            throw new IllegalArgumentException("consulta longa demais");
        }
        Consulta c = new Consulta(tokens(texto));
        String sql = c.ou();
        if (c.pos < c.tokens.size()) {
            throw new IllegalArgumentException("não entendi \"" + c.tokens.get(c.pos) + "\"");
        }
        return new Sql(sql, c.parametros);
    }

    /** Expressão SQL de um campo, para ranking e contagem distinta (o nome vai como parâmetro). */
    public static Sql campo(String nome) {
        String coluna = COLUNAS.get(nome);
        if (coluna != null) {
            return new Sql(coluna, List.of());
        }
        validarNome(nome);
        return new Sql("campos->>?", List.of(nome));
    }

    public static boolean ehColuna(String nome) {
        return COLUNAS.containsKey(nome);
    }

    // ================================================================================== gramática

    private String ou() {
        String esq = e();
        while (vem("OR")) {
            pos++;
            esq = "(" + esq + " OR " + e() + ")";
        }
        return esq;
    }

    private String e() {
        String esq = negacao();
        while (pos < tokens.size() && !vem("OR") && !vem(")")) {
            if (vem("AND")) {
                pos++;
            }
            esq = "(" + esq + " AND " + negacao() + ")";
        }
        return esq;
    }

    private String negacao() {
        if (vem("NOT")) {
            pos++;
            return "NOT " + negacao();
        }
        String t = atual();
        if (t != null && t.length() > 1 && t.startsWith("-") && !t.startsWith("-\"")) {
            tokens.set(pos, t.substring(1));
            return "NOT " + negacao();
        }
        return primario();
    }

    private String primario() {
        String t = atual();
        if (t == null) {
            throw new IllegalArgumentException("a consulta terminou no meio");
        }
        if (t.equals("(")) {
            pos++;
            String dentro = ou();
            if (!vem(")")) {
                throw new IllegalArgumentException("faltou fechar o parêntese");
            }
            pos++;
            return "(" + dentro + ")";
        }
        if (t.equals(")") || t.equals("AND") || t.equals("OR")) {
            throw new IllegalArgumentException("\"" + t + "\" fora do lugar");
        }
        pos++;
        int dois = t.startsWith("\"") ? -1 : t.indexOf(':');
        // COALESCE: sem o campo, o termo é falso (e "NOT termo" verdadeiro), em vez de nulo
        if (dois > 0) {
            return "COALESCE(" + termoCampo(t.substring(0, dois), aspas(t.substring(dois + 1))) + ", FALSE)";
        }
        return textoLivre(aspas(t));
    }

    private String textoLivre(String valor) {
        parametros.add("%" + escaparLike(valor) + "%");
        return "mensagem ILIKE ?";
    }

    private String termoCampo(String nome, String valor) {
        if (valor.isEmpty()) {
            throw new IllegalArgumentException("faltou o valor de " + nome);
        }
        if (nome.equals("_exists_")) {
            if (COLUNAS.containsKey(valor)) {
                return COLUNAS.get(valor) + " IS NOT NULL";
            }
            validarNome(valor);
            parametros.add(valor);
            return "jsonb_exists(campos, ?)";
        }
        String coluna = COLUNAS.get(nome);
        if (coluna == null) {
            validarNome(nome);
        }
        // comparação numérica
        for (String op : new String[]{"<=", ">=", "<", ">"}) {
            if (valor.startsWith(op) && NUMERO.matcher(valor.substring(op.length())).matches()) {
                double n = Double.parseDouble(valor.substring(op.length()));
                if (coluna != null) {
                    if (!coluna.equals("nivel")) {
                        throw new IllegalArgumentException(nome + " não é numérico");
                    }
                    parametros.add((int) n);
                    return "nivel " + op + " ?";
                }
                parametros.add(nome);
                parametros.add(nome);
                parametros.add(n);
                return "(CASE WHEN campos->>? ~ '^-?[0-9]+(\\.[0-9]+)?$' THEN (campos->>?)::numeric END) " + op + " ?";
            }
        }
        boolean curinga = valor.contains("*");
        if (coluna != null) {
            if (coluna.equals("nivel")) {
                if (!NUMERO.matcher(valor).matches()) {
                    throw new IllegalArgumentException("nível é um número de 0 a 7");
                }
                parametros.add(Integer.parseInt(valor));
                return "nivel = ?";
            }
            if (coluna.equals("mensagem")) {
                return textoLivre(valor.replace("*", ""));
            }
            if (curinga) {
                parametros.add(curinga(valor));
                return coluna + " ILIKE ?";
            }
            parametros.add(valor);
            return coluna + " = ?";
        }
        if (curinga) {
            parametros.add(nome);
            parametros.add(curinga(valor));
            return "campos->>? ILIKE ?";
        }
        // igualdade: usa o índice GIN (campos @> {"nome": "valor"})
        parametros.add(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put(nome, valor).toString());
        return "campos @> CAST(? AS jsonb)";
    }

    // ================================================================================== tokens

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(' || c == ')') {
                out.add(String.valueOf(c));
                i++;
            } else {
                StringBuilder b = new StringBuilder();
                boolean emAspas = false;
                while (i < s.length()) {
                    char d = s.charAt(i);
                    if (d == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                        emAspas = !emAspas;
                    } else if (!emAspas && (Character.isWhitespace(d) || d == '(' || d == ')')) {
                        break;
                    }
                    b.append(d);
                    i++;
                }
                if (emAspas) {
                    throw new IllegalArgumentException("faltou fechar as aspas");
                }
                out.add(b.toString());
            }
        }
        return out;
    }

    private boolean vem(String t) {
        return pos < tokens.size() && tokens.get(pos).equals(t);
    }

    private String atual() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private static String aspas(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1).replace("\\\"", "\"");
        }
        return v;
    }

    private static String curinga(String valor) {
        return escaparLike(valor).replace("*", "%");
    }

    private static String escaparLike(String v) {
        return v.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void validarNome(String nome) {
        if (!NOME_CAMPO.matcher(nome).matches()) {
            throw new IllegalArgumentException("nome de campo inválido: " + nome);
        }
    }

}
