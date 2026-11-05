package cn.iocoder.yudao.module.infraestrutura.dominio;

/** Contas da capacidade, sem banco nem disco, para poderem ser testadas sozinhas. */
public final class Previsao {

    /** Com menos dias que isto, o crescimento ainda não diz nada. */
    public static final int DIAS_MINIMOS = 3;

    private Previsao() {
    }

    /** @param totalBytes 0 quando o tamanho total é desconhecido */
    public record Disco(long totalBytes, long usadoBytes, long livreBytes, int percentual, boolean pelaReserva) {
    }

    /**
     * Com reserva definida, o total é a reserva e o usado é o que o CO-TI ocupa; o livre nunca passa do que o servidor informa,
     * porque o disco pode acabar antes da reserva. Sem reserva, vale o disco que o servidor informa.
     *
     * @param reservaBytes 0 = sem reserva
     */
    public static Disco disco(long servidorTotal, long servidorLivre, long usadoCoti, long reservaBytes) {
        if (reservaBytes > 0) {
            long livre = Math.max(0, Math.min(servidorLivre, reservaBytes - usadoCoti));
            return new Disco(reservaBytes, usadoCoti, livre, percentual(reservaBytes - livre, reservaBytes), true);
        }
        long usado = Math.max(0, servidorTotal - servidorLivre);
        return new Disco(servidorTotal, usado, servidorLivre, percentual(usado, servidorTotal), false);
    }

    /** @return bytes por dia; 0 se ainda não há dias suficientes */
    public static long crescimentoPorDia(long primeiro, long ultimo, long dias) {
        return dias < DIAS_MINIMOS ? 0 : (ultimo - primeiro) / dias;
    }

    /** @return dias até acabar o espaço; nulo se os dados não crescem ou ainda não há histórico */
    public static Long diasAteEncher(long livreBytes, long crescimentoPorDia) {
        return crescimentoPorDia <= 0 ? null : livreBytes / crescimentoPorDia;
    }

    /** @return 90 ou 80 quando o uso chega a esse nível, ou 0 */
    public static int nivelDeAviso(int percentual) {
        return percentual >= 90 ? 90 : percentual >= 80 ? 80 : 0;
    }

    private static int percentual(long parte, long total) {
        return total <= 0 ? 0 : (int) Math.min(100, Math.max(0, parte * 100 / total));
    }

}
