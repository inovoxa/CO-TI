package cn.iocoder.yudao.module.logs.dominio;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Período de uma consulta: relativo ("1d" = do último dia até agora), do calendário ("hoje", "ontem"), absoluto
 * (de/até) ou "tudo".
 *
 * @param de     início (null = desde sempre)
 * @param ate    fim
 * @param rotulo como aparece no canto do quadro ("1 dia atrás - agora")
 * @param passo  quanto voltar para o período anterior de comparação (hoje compara com ontem até a mesma hora)
 */
public record Periodo(LocalDateTime de, LocalDateTime ate, String rotulo, Duration passo) {

    private static final Map<String, Duration> RELATIVOS = Map.ofEntries(Map.entry("5m", Duration.ofMinutes(5)),
            Map.entry("15m", Duration.ofMinutes(15)), Map.entry("30m", Duration.ofMinutes(30)),
            Map.entry("1h", Duration.ofHours(1)), Map.entry("4h", Duration.ofHours(4)),
            Map.entry("12h", Duration.ofHours(12)), Map.entry("1d", Duration.ofDays(1)),
            Map.entry("2d", Duration.ofDays(2)), Map.entry("7d", Duration.ofDays(7)),
            Map.entry("30d", Duration.ofDays(30)));
    private static final Map<String, String> ROTULOS = Map.ofEntries(Map.entry("5m", "5 minutos"),
            Map.entry("15m", "15 minutos"), Map.entry("30m", "30 minutos"), Map.entry("1h", "1 hora"),
            Map.entry("4h", "4 horas"), Map.entry("12h", "12 horas"), Map.entry("1d", "1 dia"),
            Map.entry("2d", "2 dias"), Map.entry("7d", "7 dias"), Map.entry("30d", "30 dias"));

    public static Periodo de(String relativo, LocalDateTime de, LocalDateTime ate, LocalDateTime agora) {
        if (de != null) {
            LocalDateTime fim = ate == null ? agora : ate;
            if (!fim.isAfter(de)) {
                throw new IllegalArgumentException("o fim do período precisa ser depois do início");
            }
            return new Periodo(de, fim, "período escolhido", Duration.between(de, fim));
        }
        if (relativo == null || relativo.isBlank() || relativo.equals("tudo")) {
            return new Periodo(null, agora, "Todo o período", null);
        }
        LocalDateTime meiaNoite = agora.toLocalDate().atStartOfDay();
        if (relativo.equals("hoje")) {
            return new Periodo(meiaNoite, agora, "Hoje", Duration.ofDays(1));
        }
        if (relativo.equals("ontem")) {
            return new Periodo(meiaNoite.minusDays(1), meiaNoite, "Ontem", Duration.ofDays(1));
        }
        Duration d = RELATIVOS.get(relativo);
        if (d == null) {
            throw new IllegalArgumentException("período desconhecido: " + relativo);
        }
        return new Periodo(agora.minus(d), agora, ROTULOS.get(relativo) + " atrás - agora", d);
    }

    /** O período de comparação logo antes (para a variação dos contadores); null em "tudo". */
    public Periodo anterior() {
        if (de == null || passo == null) {
            return null;
        }
        return new Periodo(de.minus(passo), ate.minus(passo), rotulo, passo);
    }

}
