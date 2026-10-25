package cn.iocoder.yudao.module.monitoramento.dominio;

/**
 * Estados de um monitor e a regra de transição a cada verificação. Comportamento conferido em
 * {@code server/model/monitor.js} do Uptime Kuma 2.5.7 (MIT): tentativas antes de "fora do ar", batidas importantes,
 * quando avisar e o reaviso a cada N verificações fora.
 */
public final class Estados {

    public static final int FORA = 0;
    public static final int NO_AR = 1;
    public static final int PENDENTE = 2;
    public static final int MANUTENCAO = 3;

    private Estados() {
    }

    /**
     * @param estado         novo estado
     * @param tentativa      falhas seguidas contadas até agora (zera quando volta)
     * @param quedasSeguidas verificações fora desde o último aviso (para o reaviso)
     * @param importante     mudou de estado: vira evento no histórico
     * @param avisar         manda aviso de mudança (caiu ou voltou)
     * @param reaviso        manda aviso repetido de que continua fora
     */
    public record Transicao(int estado, int tentativa, int quedasSeguidas, boolean importante, boolean avisar, boolean reaviso) {
    }

    /**
     * @param anterior        estado anterior; null na primeira verificação
     * @param tentativaAtual  falhas seguidas antes desta verificação
     * @param quedas          verificações fora desde o último aviso
     * @param sucesso         resultado da verificação (já invertido, se o monitor é invertido)
     * @param emManutencao    dentro de uma janela de manutenção (não verifica, não avisa)
     * @param maxTentativas   falhas que ficam como pendente antes de "fora do ar" (0 = cai na primeira)
     * @param reenviarACada   repete o aviso a cada N verificações fora (0 = não repete)
     */
    public static Transicao avaliar(Integer anterior, int tentativaAtual, int quedas, boolean sucesso, boolean emManutencao,
                                    int maxTentativas, int reenviarACada) {
        boolean primeira = anterior == null;
        int estado;
        int tentativa;
        if (emManutencao) {
            estado = MANUTENCAO;
            tentativa = 0;
        } else if (sucesso) {
            estado = NO_AR;
            tentativa = 0;
        } else if (maxTentativas > 0 && tentativaAtual < maxTentativas) {
            estado = PENDENTE;
            tentativa = tentativaAtual + 1;
        } else {
            // continua contando enquanto está fora
            estado = FORA;
            tentativa = tentativaAtual + 1;
        }
        boolean importante = importante(primeira, anterior, estado);
        boolean avisar = importanteParaAviso(primeira, anterior, estado) && (!primeira || estado == FORA);
        int novasQuedas = 0;
        boolean reaviso = false;
        if (estado == FORA && reenviarACada > 0 && !importante) {
            novasQuedas = quedas + 1;
            if (novasQuedas >= reenviarACada) {
                reaviso = true;
                novasQuedas = 0;
            }
        }
        return new Transicao(estado, tentativa, novasQuedas, importante, avisar, reaviso);
    }

    /** Mudou de estado de um jeito que interessa ao histórico (no ar ↔ fora, entrada e saída da manutenção). */
    static boolean importante(boolean primeira, Integer anterior, int atual) {
        if (primeira) {
            return true;
        }
        int a = anterior;
        return (a == FORA && atual == MANUTENCAO) || (a == NO_AR && atual == MANUTENCAO)
                || (a == MANUTENCAO && atual == FORA) || (a == MANUTENCAO && atual == NO_AR)
                || (a == NO_AR && atual == FORA) || (a == FORA && atual == NO_AR) || (a == PENDENTE && atual == FORA);
    }

    /** Mudou de um jeito que merece aviso: caiu (também saindo da manutenção) ou voltou. */
    static boolean importanteParaAviso(boolean primeira, Integer anterior, int atual) {
        if (primeira) {
            return true;
        }
        int a = anterior;
        return (a == MANUTENCAO && atual == FORA) || (a == NO_AR && atual == FORA) || (a == FORA && atual == NO_AR)
                || (a == PENDENTE && atual == FORA);
    }

    public static String nome(Integer estado) {
        if (estado == null) {
            return "sem verificação";
        }
        return switch (estado) {
            case FORA -> "fora do ar";
            case NO_AR -> "no ar";
            case PENDENTE -> "pendente";
            case MANUTENCAO -> "em manutenção";
            default -> "desconhecido";
        };
    }

}
