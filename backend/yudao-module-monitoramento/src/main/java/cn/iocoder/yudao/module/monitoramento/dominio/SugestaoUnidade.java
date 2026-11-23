package cn.iocoder.yudao.module.monitoramento.dominio;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Unidade sugerida pelo nome do monitor ("PSF MARIA LUIZA (172.19.21.0)" → PSF MARIA LUIZA, "CER ELOA DO VALLE QUADROS"
 * → CER ELOA, "CÂMERA 01 - 6 ANDAR" → PACO MUNICIPAL 6ANDAR). Conservadora: na dúvida (empate, só palavras comuns)
 * não sugere nada e a unidade fica para escolher à mão.
 *
 * <p>Compara palavras, sem acento, parênteses nem IP, juntando vizinhas ("MARIA LUIZA" = "MARIALUIZA", "6 ANDAR" =
 * "6ANDAR") e aceitando um erro de digitação em palavras de 4+ letras ("TROVATTI" = "TROVATI"). Cada palavra pesa pelo
 * quanto é rara entre as unidades: "6ANDAR" diz mais que "PACO" ou "CER".
 */
public final class SugestaoUnidade {

    public record Unidade(Long id, String nome) {
    }

    private record Preparada(Long id, List<String> palavras, double peso) {
    }

    private static final Map<String, String> SINONIMOS = Map.ofEntries(Map.entry("JARDIM", "JD"), Map.entry("PARQUE", "PQ"),
            Map.entry("VL", "VILA"), Map.entry("PE", "PADRE"), Map.entry("PROFESSOR", "PROF"), Map.entry("PROFESSORA", "PROF"),
            Map.entry("PROFA", "PROF"), Map.entry("STA", "SANTA"), Map.entry("STO", "SANTO"), Map.entry("DR", "DOUTOR"),
            Map.entry("DRA", "DOUTORA"), Map.entry("SECRETARIA", "SEC"), Map.entry("PREF", "PREFEITO"));
    private static final Set<String> PARADAS = Set.of("DE", "DA", "DO", "DAS", "DOS", "E", "A", "O", "EM", "NA", "NO");
    /** código de equipamento no nome da unidade (SW01, RT02, M02): não ajuda a achar a unidade */
    private static final Pattern RUIDO = Pattern.compile("(SW|RT|M)\\d+");
    private static final Pattern PARENTESES = Pattern.compile("\\([^)]*\\)");
    private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}(/\\d+)?\\b");
    private static final Pattern ANDAR = Pattern.compile("(\\d+)\\s+ANDAR\\b");
    private static final Pattern SEPARADOR = Pattern.compile("[^A-Z0-9+]+");
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");

    private final List<Preparada> unidades = new ArrayList<>();
    private final Map<String, Integer> frequencia = new HashMap<>();

    public SugestaoUnidade(Collection<Unidade> lista) {
        for (Unidade u : lista) {
            if (u.id() == null || u.nome() == null) {
                continue;
            }
            List<String> p = palavras(u.nome()).stream().filter(x -> !RUIDO.matcher(x).matches()).toList();
            if (!p.isEmpty()) {
                unidades.add(new Preparada(u.id(), p, 0));
                new HashSet<>(p).forEach(x -> frequencia.merge(x, 1, Integer::sum));
            }
        }
        unidades.replaceAll(u -> new Preparada(u.id(), u.palavras(), u.palavras().stream().mapToDouble(this::peso).sum()));
    }

    /** A unidade que o nome indica, ou null quando nenhuma se destaca. */
    public Long sugerir(String nomeMonitor) {
        if (nomeMonitor == null || unidades.isEmpty()) {
            return null;
        }
        List<String> m = palavras(nomeMonitor);
        if (m.isEmpty()) {
            return null;
        }
        Set<String> mComJuncao = comJuncao(m);

        // 1) todas as palavras da unidade estão no nome; entre várias, a que explica mais do nome e depois a mais específica
        Preparada melhor = null;
        int melhorExplica = -1;
        boolean empate = false;
        double[] cobertura = new double[unidades.size()];
        boolean[] distintiva = new boolean[unidades.size()];
        for (int i = 0; i < unidades.size(); i++) {
            Preparada u = unidades.get(i);
            int[] achou = casar(u.palavras(), mComJuncao, m);
            double obtido = 0;
            boolean todas = true;
            for (int j = 0; j < achou.length; j++) {
                String x = u.palavras().get(j);
                if (achou[j] > 0) {
                    obtido += peso(x);
                    distintiva[i] |= achou[j] == EXATA && frequencia.get(x) == 1 && x.matches(".*\\d.*") && x.matches(".*[A-Z]{3}.*");
                } else {
                    todas = false;
                }
            }
            cobertura[i] = obtido / u.peso();
            if (todas) {
                int explica = explica(u.palavras(), m);
                int c = melhor == null ? 1 : explica != melhorExplica ? Integer.compare(explica, melhorExplica)
                        : Math.abs(u.peso() - melhor.peso()) < 1e-9 ? 0 : Double.compare(u.peso(), melhor.peso());
                if (c > 0) {
                    melhor = u;
                    melhorExplica = explica;
                    empate = false;
                } else if (c == 0) {
                    empate = true;
                }
            }
        }
        if (melhor != null) {
            return empate ? null : melhor.id();
        }

        // 2) o nome é a forma curta de uma unidade só ("CER JUDITH" → CER JUDITH DE BARROS BATELLI)
        List<String> essenciais = m.stream().filter(x -> x.length() >= 2 && !x.chars().allMatch(Character::isDigit)
                && !RUIDO.matcher(x).matches()).toList();
        if (essenciais.size() >= 2) {
            Long unica = null;
            int quantas = 0;
            for (Preparada u : unidades) {
                int[] achou = casar(essenciais, comJuncao(u.palavras()), u.palavras());
                if (Arrays.stream(achou).allMatch(a -> a > 0)) {
                    unica = u.id();
                    quantas++;
                }
            }
            if (quantas == 1) {
                return unica;
            }
        }

        // 3) a maior parte do peso da unidade, bem à frente da segunda; ou um código só dela ("6ANDAR")
        int a = -1, b = -1;
        for (int i = 0; i < cobertura.length; i++) {
            if (a < 0 || cobertura[i] > cobertura[a]) {
                b = a;
                a = i;
            } else if (b < 0 || cobertura[i] > cobertura[b]) {
                b = i;
            }
        }
        double folga = cobertura[a] - (b < 0 ? 0 : cobertura[b]);
        if (folga >= 0.15 && (cobertura[a] >= 0.6 || distintiva[a])) {
            return unidades.get(a).id();
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ apoio

    private static final int EXATA = 2;
    private static final int PARECIDA = 1;

    /** Para cada palavra de {@code alvo}: EXATA, PARECIDA (um erro de digitação) ou 0. */
    private static int[] casar(List<String> alvo, Set<String> outroComJuncao, List<String> outro) {
        int[] r = new int[alvo.size()];
        for (int i = 0; i < alvo.size(); i++) {
            String x = alvo.get(i);
            if (outroComJuncao.contains(x)) {
                r[i] = EXATA;
            } else if (letras(x) && x.length() >= 4) {
                for (String y : outro) {
                    if (letras(y) && y.length() >= 4 && Math.max(x.length(), y.length()) >= 5 && umaDiferenca(x, y)) {
                        r[i] = PARECIDA;
                        break;
                    }
                }
            }
        }
        for (int i = 0; i + 1 < alvo.size(); i++) {
            if (outroComJuncao.contains(alvo.get(i) + alvo.get(i + 1))) {
                r[i] = r[i + 1] = EXATA;
            }
        }
        return r;
    }

    /** Quantas letras do nome do monitor a unidade explica: desempata "SUBPREFEITURA BUENO" de "SUB PREFEITURA". */
    private static int explica(List<String> unidade, List<String> m) {
        Set<String> u = comJuncao(unidade);
        int n = 0;
        for (String y : m) {
            if (u.contains(y)) {
                n += y.length();
            }
        }
        for (int i = 0; i + 1 < m.size(); i++) {
            if (!u.contains(m.get(i)) && !u.contains(m.get(i + 1)) && u.contains(m.get(i) + m.get(i + 1))) {
                n += m.get(i).length() + m.get(i + 1).length();
            }
        }
        return n;
    }

    private double peso(String palavra) {
        return Math.log(1 + (double) unidades.size() / frequencia.getOrDefault(palavra, 1));
    }

    private static Set<String> comJuncao(List<String> p) {
        Set<String> s = new HashSet<>(p);
        for (int i = 0; i + 1 < p.size(); i++) {
            s.add(p.get(i) + p.get(i + 1));
        }
        return s;
    }

    static List<String> palavras(String nome) {
        String s = ACENTOS.matcher(Normalizer.normalize(nome, Normalizer.Form.NFD)).replaceAll("")
                .replaceAll("[ºª°]", " ").toUpperCase(Locale.ROOT);
        s = PARENTESES.matcher(s).replaceAll(" ");
        s = IP.matcher(s).replaceAll(" ");
        s = ANDAR.matcher(s).replaceAll("$1ANDAR");
        List<String> r = new ArrayList<>();
        for (String x : SEPARADOR.split(s)) {
            if (!x.isEmpty() && !PARADAS.contains(x)) {
                r.add(SINONIMOS.getOrDefault(x, x));
            }
        }
        return r;
    }

    private static boolean letras(String s) {
        return s.chars().allMatch(c -> c >= 'A' && c <= 'Z');
    }

    private static boolean umaDiferenca(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        if (a.length() == b.length()) {
            int d = 0;
            for (int i = 0; i < a.length() && d <= 1; i++) {
                d += a.charAt(i) != b.charAt(i) ? 1 : 0;
            }
            return d <= 1;
        }
        String curta = a.length() < b.length() ? a : b, longa = curta == a ? b : a;
        for (int i = 0; i < longa.length(); i++) {
            if ((longa.substring(0, i) + longa.substring(i + 1)).equals(curta)) {
                return true;
            }
        }
        return false;
    }

}
