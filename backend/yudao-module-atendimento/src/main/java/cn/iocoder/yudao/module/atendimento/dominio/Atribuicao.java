package cn.iocoder.yudao.module.atendimento.dominio;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Escolha do atendente na atribuição automática (docs/specs/atendimento-atribuicao.md).
 */
public final class Atribuicao {

    /** Quanto tempo sem sinal do navegador até a pessoa deixar de contar como disponível. */
    public static final long SINAL_MINUTOS = 3;

    /**
     * @param disponivel marcou "disponível" e deu sinal há menos de {@link #SINAL_MINUTOS} minutos
     * @param abertasNaCaixa conversas abertas com ela nesta caixa (para o limite)
     * @param abertasTotal conversas abertas com ela em todas as caixas (para equilibrar)
     */
    public record Candidato(long userId, boolean disponivel, int abertasNaCaixa, int abertasTotal,
                            LocalDateTime ultimaAtribuicao) {
    }

    private Atribuicao() {
    }

    /** @param limite máximo de conversas abertas por pessoa na caixa; 0 = sem limite */
    public static Optional<Long> escolher(List<Candidato> candidatos, int limite) {
        return candidatos.stream()
                .filter(Candidato::disponivel)
                .filter(c -> limite <= 0 || c.abertasNaCaixa() < limite)
                .min(Comparator.comparingInt(Candidato::abertasTotal)
                        .thenComparing(Candidato::ultimaAtribuicao, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(Candidato::userId))
                .map(Candidato::userId);
    }

    public static boolean disponivel(String disponibilidade, LocalDateTime ultimoSinal, LocalDateTime agora) {
        return "online".equals(disponibilidade) && ultimoSinal != null && !ultimoSinal.isBefore(agora.minusMinutes(SINAL_MINUTOS));
    }

}
