package cn.iocoder.yudao.module.atendimento.dominio;

import java.text.Normalizer;
import java.util.*;

/**
 * Reordena os artigos candidatos (vindos da busca de texto do PostgreSQL) pela pergunta: termo raro entre os
 * candidatos pesa mais que termo comum (IDF), e termo no título pesa o dobro. O ts_rank sozinho não considera a
 * raridade e deixava "VPN" valer o mesmo que o nome do sistema que o contato citou.
 */
public final class Relevancia {

    private Relevancia() {
    }

    private static final Set<String> VAZIAS = Set.of("como", "para", "com", "uma", "que", "nao", "das", "dos", "por", "meu", "minha",
            "sobre", "esta", "este", "isso", "qual", "quais", "tem", "ter", "sim", "voce", "pode", "posso", "preciso", "quero",
            "bom", "dia", "boa", "tarde", "noite", "obrigado", "obrigada", "ola", "oi");

    public record Candidato<T>(T item, String titulo, String texto) {
    }

    /** Termos da pergunta: sem acento, 3+ letras, sem palavras vazias; palavras longas perdem a terminação (radical simples: "acesso" e "acessar" casam). */
    public static List<String> termos(String pergunta) {
        if (pergunta == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String p : simples(pergunta).split("[^\\p{L}\\p{N}]+")) {
            if (p.length() >= 3 && !VAZIAS.contains(p)) {
                out.add(p.length() > 5 ? p.substring(0, Math.max(5, p.length() - 3)) : p);
            }
        }
        return new ArrayList<>(out);
    }

    public static <T> List<T> ordenar(String pergunta, List<Candidato<T>> candidatos, int limite) {
        return pontuar(pergunta, candidatos).stream().limit(limite).map(Map.Entry::getKey).toList();
    }

    /** Todos os candidatos com a nota, do mais relevante para o menos (sem termos na pergunta: nota zero). */
    public static <T> List<Map.Entry<T, Double>> pontuar(String pergunta, List<Candidato<T>> candidatos) {
        List<String> termos = termos(pergunta);
        if (termos.isEmpty() || candidatos.isEmpty()) {
            return candidatos.stream().map(c -> Map.entry(c.item(), 0.0)).toList();
        }
        int n = candidatos.size();
        List<String[]> textos = candidatos.stream().map(c -> new String[]{simples(c.titulo()), simples(c.texto())}).toList();
        Map<String, Double> idf = new HashMap<>();
        for (String t : termos) {
            long df = textos.stream().filter(x -> contem(x[0], t) || contem(x[1], t)).count();
            idf.put(t, df == 0 ? 0 : Math.log(1 + (double) n / df));
        }
        List<Integer> ordem = new ArrayList<>();
        double[] nota = new double[n];
        for (int i = 0; i < n; i++) {
            ordem.add(i);
            for (String t : termos) {
                if (contem(textos.get(i)[0], t)) {
                    nota[i] += 2 * idf.get(t);
                } else if (contem(textos.get(i)[1], t)) {
                    nota[i] += idf.get(t);
                }
            }
        }
        // empate: mantém a ordem da busca (fixados e ts_rank)
        ordem.sort((a, b) -> Double.compare(nota[b], nota[a]) != 0 ? Double.compare(nota[b], nota[a]) : Integer.compare(a, b));
        return ordem.stream().map(i -> Map.entry(candidatos.get(i).item(), nota[i])).toList();
    }

    /** O termo aparece como início de palavra. */
    private static boolean contem(String texto, String termo) {
        int i = texto.indexOf(termo);
        while (i >= 0) {
            if (i == 0 || !Character.isLetterOrDigit(texto.charAt(i - 1))) {
                return true;
            }
            i = texto.indexOf(termo, i + 1);
        }
        return false;
    }

    private static String simples(String s) {
        return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

}
