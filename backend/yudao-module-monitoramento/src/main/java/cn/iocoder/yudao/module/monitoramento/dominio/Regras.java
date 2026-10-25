package cn.iocoder.yudao.module.monitoramento.dominio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras das verificações, sem dependência de servidor: códigos HTTP aceitos, consulta JSON, janelas de manutenção e
 * a leitura da saída do {@code ping}.
 */
public final class Regras {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern TEMPO_PING = Pattern.compile("time[=<]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*ms");
    /** Host para ping/TCP/DNS: nome ou IP, sem espaço nem opção de linha de comando. */
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.:\\-]{0,252}$");
    private static final Pattern CAMINHO = Pattern.compile("\\G(?:\\.?([A-Za-z_$][A-Za-z0-9_$\\-]*)|\\[(\\d+)]|\\[\"([^\"]*)\"]|\\['([^']*)'])");

    private Regras() {
    }

    // ================================================================================== HTTP

    /**
     * Códigos aceitos: faixas e códigos separados por vírgula ("200-299,301"). Aceita também a lista em JSON do Kuma
     * ({@code ["200-299"]}).
     */
    public static boolean codigoAceito(String especificacao, int codigo) {
        String e = especificacao == null || especificacao.isBlank() ? "200-299" : especificacao;
        for (String parte : e.replaceAll("[\\[\\]\"\\s]", "").split("[,;]")) {
            if (parte.isEmpty()) {
                continue;
            }
            try {
                int traco = parte.indexOf('-');
                if (traco > 0) {
                    if (codigo >= Integer.parseInt(parte.substring(0, traco)) && codigo <= Integer.parseInt(parte.substring(traco + 1))) {
                        return true;
                    }
                } else if (codigo == Integer.parseInt(parte)) {
                    return true;
                }
            } catch (NumberFormatException ignorado) {
                // parte inválida não aceita nada
            }
        }
        return false;
    }

    public static boolean codigosValidos(String especificacao) {
        if (especificacao == null || especificacao.isBlank()) {
            return true;
        }
        return especificacao.replaceAll("[\\[\\]\"\\s]", "").matches("(\\d{3}(-\\d{3})?)([,;]\\d{3}(-\\d{3})?)*");
    }

    // ================================================================================== JSON

    public record ResultadoJson(boolean ok, String valor, String erro) {
    }

    /**
     * Lê um valor do JSON pelo caminho (ex.: {@code status}, {@code $.dados.servicos[0].estado},
     * {@code itens["nome com espaço"]}) e compara com o esperado. Operadores: ==, !=, <, <=, >, >=, contem. Números
     * são comparados como números; o resto, como texto. É um subconjunto das expressões JSONata do Kuma: caminhos
     * simples.
     */
    public static ResultadoJson consultaJson(String corpo, String caminho, String operador, String esperado) {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(corpo);
        } catch (Exception e) {
            return new ResultadoJson(false, null, "a resposta não é JSON");
        }
        if (raiz == null) {
            return new ResultadoJson(false, null, "a resposta está vazia");
        }
        JsonNode no;
        try {
            no = navegar(raiz, caminho);
        } catch (IllegalArgumentException e) {
            return new ResultadoJson(false, null, e.getMessage());
        }
        if (no == null || no.isMissingNode()) {
            return new ResultadoJson(false, null, "o caminho '" + caminho + "' não existe na resposta");
        }
        String valor = no.isValueNode() ? no.asText() : no.toString();
        String op = operador == null || operador.isBlank() ? "==" : operador.trim();
        String esp = esperado == null ? "" : esperado;
        BigDecimal a = numero(valor);
        BigDecimal b = numero(esp);
        boolean ok = switch (op) {
            case "==" -> a != null && b != null ? a.compareTo(b) == 0 : valor.equals(esp);
            case "!=" -> a != null && b != null ? a.compareTo(b) != 0 : !valor.equals(esp);
            case "<" -> a != null && b != null && a.compareTo(b) < 0;
            case "<=" -> a != null && b != null && a.compareTo(b) <= 0;
            case ">" -> a != null && b != null && a.compareTo(b) > 0;
            case ">=" -> a != null && b != null && a.compareTo(b) >= 0;
            case "contem", "contains" -> valor.contains(esp);
            default -> throw new IllegalArgumentException("operador inválido: " + op);
        };
        return new ResultadoJson(ok, valor, null);
    }

    public static boolean caminhoValido(String caminho) {
        try {
            tokens(caminho);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static JsonNode navegar(JsonNode raiz, String caminho) {
        JsonNode no = raiz;
        for (Object t : tokens(caminho)) {
            if (no == null) {
                return null;
            }
            no = t instanceof Integer i ? no.get(i) : no.get((String) t);
        }
        return no;
    }

    private static List<Object> tokens(String caminho) {
        String c = caminho == null ? "" : caminho.trim();
        if (c.startsWith("$")) {
            c = c.substring(1);
        }
        List<Object> out = new ArrayList<>();
        if (c.isEmpty()) {
            return out;
        }
        Matcher m = CAMINHO.matcher(c);
        int fim = 0;
        while (fim < c.length() && m.find() && m.start() == fim) {
            if (m.group(1) != null) {
                out.add(m.group(1));
            } else if (m.group(2) != null) {
                out.add(Integer.parseInt(m.group(2)));
            } else {
                out.add(m.group(3) != null ? m.group(3) : m.group(4));
            }
            fim = m.end();
        }
        if (fim != c.length()) {
            throw new IllegalArgumentException("caminho JSON não suportado: '" + caminho + "' (use algo como dados.itens[0].estado)");
        }
        return out;
    }

    private static BigDecimal numero(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ================================================================================== manutenção

    /**
     * A janela está valendo agora?
     *
     * @param tipo        manual (vale enquanto ativa) | unica (de início a fim) | semanal (dias e horário)
     * @param diasSemana  1 (segunda) a 7 (domingo), separados por vírgula
     * @param horaFim     menor ou igual ao início = atravessa a meia-noite (o dia marcado é o do início)
     */
    public static boolean manutencaoValendo(String tipo, boolean ativa, LocalDateTime inicio, LocalDateTime fim, String diasSemana,
                                            LocalTime horaInicio, LocalTime horaFim, LocalDateTime agora) {
        if (!ativa || tipo == null) {
            return false;
        }
        return switch (tipo) {
            case "manual" -> true;
            case "unica" -> inicio != null && fim != null && !agora.isBefore(inicio) && agora.isBefore(fim);
            case "semanal" -> {
                if (horaInicio == null || horaFim == null) {
                    yield false;
                }
                Set<Integer> dias = dias(diasSemana);
                LocalTime t = agora.toLocalTime();
                DayOfWeek hoje = agora.getDayOfWeek();
                if (horaFim.isAfter(horaInicio)) {
                    yield dias.contains(hoje.getValue()) && !t.isBefore(horaInicio) && t.isBefore(horaFim);
                }
                // atravessa a meia-noite: vale do início até 24 h no dia marcado e de 0 h até o fim no dia seguinte
                yield (dias.contains(hoje.getValue()) && !t.isBefore(horaInicio))
                        || (dias.contains(hoje.minus(1).getValue()) && t.isBefore(horaFim));
            }
            default -> false;
        };
    }

    public static Set<Integer> dias(String diasSemana) {
        Set<Integer> out = new TreeSet<>();
        if (diasSemana == null) {
            return out;
        }
        for (String d : diasSemana.split("[,;\\s]+")) {
            try {
                int n = Integer.parseInt(d.trim());
                if (n >= 1 && n <= 7) {
                    out.add(n);
                }
            } catch (NumberFormatException ignorado) {
                // ignora
            }
        }
        return out;
    }

    // ================================================================================== ping e host

    /** Tempo em ms da saída do ping (Linux/iputils ou busybox); null se não respondeu. */
    public static Integer tempoPing(String saida) {
        if (saida == null) {
            return null;
        }
        Matcher m = TEMPO_PING.matcher(saida);
        if (m.find()) {
            return (int) Math.max(1, Math.round(Double.parseDouble(m.group(1))));
        }
        return null;
    }

    public static boolean hostValido(String host) {
        return host != null && HOST.matcher(host).matches() && !host.contains("..");
    }

}
