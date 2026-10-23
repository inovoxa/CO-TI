package cn.iocoder.yudao.module.itsm.dominio;

import java.util.Map;

/**
 * Urgência, impacto e prioridade (escala 1–5; prioridade 6 = "Crítica", só definida à mão).
 *
 * <p>A prioridade sai da matriz urgência × impacto. A matriz padrão é a mesma que o GLPI instala
 * (install/empty_data.php, chave priority_matrix); a instalação pode trocá-la na configuração
 * {@code itsm.matriz-prioridade}. Sem matriz para o par, vale a média arredondada (como no GLPI).
 */
public final class Prioridade {

    public static final int MINIMA = 1;
    public static final int MAXIMA = 5;
    public static final int CRITICA = 6;

    private static final String[] ROTULOS = {"", "Muito baixa", "Baixa", "Média", "Alta", "Muito alta", "Crítica"};

    /** [urgência][impacto] → prioridade */
    static final int[][] MATRIZ_GLPI = {
            {},
            {0, 1, 1, 2, 2, 2},
            {0, 1, 2, 2, 3, 3},
            {0, 2, 2, 3, 4, 4},
            {0, 2, 3, 4, 4, 5},
            {0, 2, 3, 4, 5, 5},
    };

    private Prioridade() {
    }

    public static int calcular(int urgencia, int impacto) {
        return calcular(urgencia, impacto, null);
    }

    /**
     * @param matriz matriz configurada (JSON do GLPI: {"urgência": {"impacto": prioridade}}), ou null para a padrão
     */
    public static int calcular(int urgencia, int impacto, Map<String, Map<String, Integer>> matriz) {
        validarEscala(urgencia, "urgência");
        validarEscala(impacto, "impacto");
        if (matriz != null) {
            Map<String, Integer> linha = matriz.get(String.valueOf(urgencia));
            Integer p = linha == null ? null : linha.get(String.valueOf(impacto));
            if (p != null && p >= MINIMA && p <= CRITICA) {
                return p;
            }
            return Math.round((urgencia + impacto) / 2f);
        }
        return MATRIZ_GLPI[urgencia][impacto];
    }

    public static String rotulo(int valor) {
        return valor >= MINIMA && valor <= CRITICA ? ROTULOS[valor] : "?";
    }

    public static void validarEscala(int valor, String campo) {
        if (valor < MINIMA || valor > MAXIMA) {
            throw new IllegalArgumentException("A " + campo + " vai de 1 a 5");
        }
    }

}
