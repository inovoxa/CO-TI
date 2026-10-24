package cn.iocoder.yudao.module.itsm.dominio;

import java.time.*;
import java.util.*;

/**
 * Calendário de expediente: períodos de trabalho por dia da semana e feriados. Sem banco e sem Spring.
 *
 * <p>Porta do GLPI 12 (Calendar::computeEndDate, Calendar::getActiveTimeBetween e CalendarSegment). Três modos de
 * somar um prazo, como no GLPI:
 * <ul>
 *   <li><b>horas úteis</b> (SLA em minutos ou horas): só conta o tempo dentro dos períodos de trabalho;</li>
 *   <li><b>dias úteis</b> (SLA em dias ou meses): pula dias sem expediente e feriados, mantendo a hora; se cair
 *       depois do fim do expediente, vai para o fim do expediente daquele dia;</li>
 *   <li><b>fim do expediente</b>: conta N dias úteis e termina no fim do expediente do último dia.</li>
 * </ul>
 * O "tempo adicional" (pendências) é somado depois, sempre em horas úteis.
 *
 * <p>Os dias da semana seguem o padrão ISO do Java (segunda = 1 ... domingo = 7). Um período termina antes do
 * segundo final (intervalo [início, fim)); fim 24:00 é representado por 86400 segundos.
 */
public final class Calendario {

    public static final long DIA = 86_400L;

    /** [início, fim) em segundos desde a meia-noite */
    public record Periodo(int inicio, int fim) {
        public Periodo {
            if (inicio < 0 || fim > DIA || fim <= inicio) {
                throw new IllegalArgumentException("Período inválido: " + inicio + "–" + fim);
            }
        }

        public static Periodo de(LocalTime inicio, LocalTime fim) {
            int f = fim.equals(LocalTime.MIDNIGHT) ? (int) DIA : fim.toSecondOfDay();
            return new Periodo(inicio.toSecondOfDay(), f);
        }
    }

    /** Feriado de um dia ou intervalo; perpétuo = repete todo ano (ignora o ano). */
    public record Feriado(LocalDate inicio, LocalDate fim, boolean perpetuo) {
        boolean cobre(LocalDate d) {
            if (!perpetuo) {
                return !d.isBefore(inicio) && !d.isAfter(fim);
            }
            MonthDay md = MonthDay.from(d);
            MonthDay a = MonthDay.from(inicio);
            MonthDay b = MonthDay.from(fim);
            return a.compareTo(b) <= 0 ? !md.isBefore(a) && !md.isAfter(b) : !md.isBefore(a) || !md.isAfter(b);
        }
    }

    private final Map<DayOfWeek, List<Periodo>> periodos = new EnumMap<>(DayOfWeek.class);
    private final List<Feriado> feriados;

    public Calendario(Map<DayOfWeek, List<Periodo>> periodos, List<Feriado> feriados) {
        for (DayOfWeek d : DayOfWeek.values()) {
            List<Periodo> l = new ArrayList<>(periodos.getOrDefault(d, List.of()));
            l.sort(Comparator.comparingInt(Periodo::inicio));
            this.periodos.put(d, List.copyOf(l));
        }
        this.feriados = List.copyOf(feriados);
    }

    /** Segunda a sexta, 08:00–12:00 e 13:00–18:00 (padrão sugerido na tela). */
    public static Calendario comercial(List<Feriado> feriados) {
        Map<DayOfWeek, List<Periodo>> p = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            p.put(d, List.of(Periodo.de(LocalTime.of(8, 0), LocalTime.of(12, 0)), Periodo.de(LocalTime.of(13, 0), LocalTime.of(18, 0))));
        }
        return new Calendario(p, feriados);
    }

    public boolean temDiaUtil() {
        return periodos.values().stream().anyMatch(l -> !l.isEmpty());
    }

    public boolean feriado(LocalDate d) {
        return feriados.stream().anyMatch(f -> f.cobre(d));
    }

    /** Dia com expediente e que não é feriado. */
    public boolean diaUtil(LocalDate d) {
        return !feriado(d) && duracaoDia(d.getDayOfWeek()) > 0;
    }

    public long duracaoDia(DayOfWeek d) {
        return periodos.get(d).stream().mapToLong(p -> p.fim() - p.inicio()).sum();
    }

    public boolean emExpediente(LocalDateTime t) {
        if (feriado(t.toLocalDate())) {
            return false;
        }
        int s = t.toLocalTime().toSecondOfDay();
        return periodos.get(t.getDayOfWeek()).stream().anyMatch(p -> s >= p.inicio() && s < p.fim());
    }

    // ================================================================== tempo ativo

    /** Tempo de expediente entre dois instantes (GLPI: getActiveTimeBetween). */
    public long ativoEntre(LocalDateTime de, LocalDateTime ate) {
        if (de == null || ate == null || !ate.isAfter(de)) {
            return 0;
        }
        long total = 0;
        for (LocalDate d = de.toLocalDate(); !d.isAfter(ate.toLocalDate()); d = d.plusDays(1)) {
            if (feriado(d)) {
                continue;
            }
            int ini = d.equals(de.toLocalDate()) ? de.toLocalTime().toSecondOfDay() : 0;
            int fim = d.equals(ate.toLocalDate()) ? ate.toLocalTime().toSecondOfDay() : (int) DIA;
            total += ativoNoDia(d.getDayOfWeek(), ini, fim);
        }
        return total;
    }

    private long ativoNoDia(DayOfWeek dia, int ini, int fim) {
        long t = 0;
        for (Periodo p : periodos.get(dia)) {
            int a = Math.max(p.inicio(), ini);
            int b = Math.min(p.fim(), fim);
            if (b > a) {
                t += b - a;
            }
        }
        return t;
    }

    // ================================================================== soma de prazo

    /**
     * Data final de um prazo (GLPI: computeEndDate, prazo positivo).
     *
     * @param segundos        prazo em segundos (dias e meses já convertidos: 1 dia = 86400, 1 mês = 30 dias)
     * @param adicional       tempo extra em segundos (pendências), somado depois em horas úteis
     * @param emDias          prazo definido em dias ou meses
     * @param fimDoExpediente terminar no fim do expediente do último dia
     */
    public LocalDateTime somar(LocalDateTime inicio, long segundos, long adicional, boolean emDias, boolean fimDoExpediente) {
        if (!temDiaUtil()) {
            throw new IllegalStateException("Calendário sem nenhum período de expediente");
        }
        LocalDateTime r;
        if (fimDoExpediente) {
            r = somarAteFimDoExpediente(inicio, segundos);
        } else if (emDias) {
            r = somarDiasUteis(inicio, segundos);
        } else {
            r = somarHorasUteis(inicio, segundos);
        }
        return adicional > 0 ? somarHorasUteis(r, adicional) : r;
    }

    LocalDateTime somarHorasUteis(LocalDateTime inicio, long segundos) {
        long resta = Math.max(0, segundos);
        LocalDate dia = inicio.toLocalDate();
        int de = inicio.toLocalTime().toSecondOfDay();
        for (int guarda = 0; guarda < 366 * 50; guarda++) {
            if (!feriado(dia)) {
                for (Periodo p : periodos.get(dia.getDayOfWeek())) {
                    if (p.fim() <= de) {
                        continue;
                    }
                    int a = Math.max(p.inicio(), de);
                    long len = p.fim() - a;
                    if (resta < len) {
                        return dia.atStartOfDay().plusSeconds(a + resta);
                    }
                    resta -= len;
                }
            }
            dia = dia.plusDays(1);
            de = 0;
        }
        throw new IllegalStateException("Prazo além de 50 anos");
    }

    LocalDateTime somarDiasUteis(LocalDateTime inicio, long segundos) {
        LocalDateTime t = inicio;
        // começa num dia sem expediente: vai para o início do próximo dia útil
        if (!diaUtil(t.toLocalDate())) {
            LocalDate d = proximoDiaUtil(t.toLocalDate());
            t = d.atStartOfDay().plusSeconds(primeiraHora(d.getDayOfWeek()));
        }
        long resta = segundos;
        while (resta > 0) {
            t = t.plusDays(1);
            if (diaUtil(t.toLocalDate())) {
                resta -= DIA;
            }
            if (resta < 0) {
                t = t.plusSeconds(resta);
            }
        }
        int ultima = ultimaHora(t.getDayOfWeek());
        if (ultima >= 0 && t.toLocalTime().toSecondOfDay() > ultima) {
            t = t.toLocalDate().atStartOfDay().plusSeconds(ultima);
        }
        return t;
    }

    LocalDateTime somarAteFimDoExpediente(LocalDateTime inicio, long segundos) {
        double dias = (double) segundos / DIA;
        LocalDate d = inicio.toLocalDate();
        if (!diaUtil(d)) {
            d = proximoDiaUtil(d);
        }
        while (dias > 0) {
            if (diaUtil(d)) {
                dias--;
            }
            d = d.plusDays(1);
        }
        if (!diaUtil(d)) {
            d = proximoDiaUtil(d);
        }
        return d.atStartOfDay().plusSeconds(ultimaHora(d.getDayOfWeek()));
    }

    private LocalDate proximoDiaUtil(LocalDate d) {
        LocalDate x = d;
        for (int i = 0; i < 366 * 50; i++) {
            if (diaUtil(x)) {
                return x;
            }
            x = x.plusDays(1);
        }
        throw new IllegalStateException("Nenhum dia útil nos próximos 50 anos");
    }

    private int primeiraHora(DayOfWeek d) {
        List<Periodo> l = periodos.get(d);
        return l.isEmpty() ? 0 : l.get(0).inicio();
    }

    /** Fim do último período do dia (ou -1 se não há expediente). */
    private int ultimaHora(DayOfWeek d) {
        return periodos.get(d).stream().mapToInt(Periodo::fim).max().orElse(-1);
    }

}
