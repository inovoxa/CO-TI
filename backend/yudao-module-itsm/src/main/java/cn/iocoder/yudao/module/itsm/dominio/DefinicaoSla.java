package cn.iocoder.yudao.module.itsm.dominio;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Um SLA pronto para calcular prazos (GLPI: LevelAgreement::getTime, computeDate e getActiveTimeBetween).
 *
 * @param tempo           quantidade
 * @param unidade         minuto | hora | dia | mes (mês = 30 dias, como no GLPI)
 * @param calendario      expediente; null = 24 h por dia, 7 dias por semana
 * @param fimDoExpediente terminar no fim do expediente (só para dia/mês)
 */
public record DefinicaoSla(long tempo, String unidade, Calendario calendario, boolean fimDoExpediente) {

    public DefinicaoSla {
        if (tempo < 0) {
            throw new IllegalArgumentException("O tempo do SLA não pode ser negativo");
        }
        if (!java.util.List.of("minuto", "hora", "dia", "mes").contains(unidade)) {
            throw new IllegalArgumentException("Unidade de SLA desconhecida: " + unidade);
        }
        if (fimDoExpediente && !emDias(unidade)) {
            throw new IllegalArgumentException("\"Fim do expediente\" só vale para SLA em dias ou meses");
        }
        if (calendario != null && !calendario.temDiaUtil()) {
            // calendário inválido no GLPI cai para 24x7
            calendario = null;
        }
    }

    public long segundos() {
        return switch (unidade) {
            case "minuto" -> tempo * 60;
            case "hora" -> tempo * 3600;
            case "dia" -> tempo * Calendario.DIA;
            default -> tempo * 30 * Calendario.DIA;
        };
    }

    /** Prazo a partir do início, somando o tempo em pendência já descontado do expediente. */
    public LocalDateTime prazo(LocalDateTime inicio, long esperaSeg) {
        if (calendario == null) {
            return inicio.plusSeconds(segundos() + Math.max(0, esperaSeg));
        }
        return calendario.somar(inicio, segundos(), Math.max(0, esperaSeg), emDias(unidade), fimDoExpediente);
    }

    /** Tempo que conta para o SLA entre dois instantes (só expediente, se houver calendário). */
    public long ativoEntre(LocalDateTime de, LocalDateTime ate) {
        if (de == null || ate == null || !ate.isAfter(de)) {
            return 0;
        }
        return calendario == null ? Duration.between(de, ate).getSeconds() : calendario.ativoEntre(de, ate);
    }

    private static boolean emDias(String unidade) {
        return "dia".equals(unidade) || "mes".equals(unidade);
    }

}
