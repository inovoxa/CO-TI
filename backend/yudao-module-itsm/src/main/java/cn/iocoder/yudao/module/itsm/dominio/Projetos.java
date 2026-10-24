package cn.iocoder.yudao.module.itsm.dominio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Regras de projeto que não dependem de banco (GLPI: Project::recalculatePercentDone e ProjectTask).
 */
public final class Projetos {

    public static final int PLANEJADO = 1;
    public static final int EM_ANDAMENTO = 2;
    public static final int PAUSADO = 3;
    public static final int CONCLUIDO = 4;
    public static final int CANCELADO = 5;

    public static final int TAREFA_A_FAZER = 1;
    public static final int TAREFA_EM_ANDAMENTO = 2;
    public static final int TAREFA_CONCLUIDA = 3;
    public static final int TAREFA_CANCELADA = 4;

    /** O que entra no cálculo do andamento. */
    public record Tarefa(int situacao, int percentual, BigDecimal horasPrevistas) {
    }

    private Projetos() {
    }

    /**
     * Andamento do projeto pelas tarefas: canceladas não contam; concluída vale 100%. Se todas as tarefas têm horas
     * previstas, a média é ponderada pelas horas (tarefa grande pesa mais); senão, média simples.
     */
    public static int percentual(List<Tarefa> tarefas) {
        List<Tarefa> validas = tarefas.stream().filter(t -> t.situacao() != TAREFA_CANCELADA).toList();
        if (validas.isEmpty()) {
            return 0;
        }
        boolean ponderar = validas.stream().allMatch(t -> t.horasPrevistas() != null && t.horasPrevistas().signum() > 0);
        double soma = 0;
        double pesos = 0;
        for (Tarefa t : validas) {
            double peso = ponderar ? t.horasPrevistas().doubleValue() : 1;
            soma += feito(t) * peso;
            pesos += peso;
        }
        return (int) Math.round(soma / pesos);
    }

    /** Situação da tarefa e percentual andam juntos: concluída = 100; 100% = concluída; começar = em andamento. */
    public static int percentualDaTarefa(int situacao, int percentual) {
        if (situacao == TAREFA_CONCLUIDA) {
            return 100;
        }
        return Math.max(0, Math.min(percentual, situacao == TAREFA_CANCELADA ? 100 : 99));
    }

    public static int situacaoDaTarefa(int situacao, int percentual) {
        if (situacao == TAREFA_CANCELADA) {
            return situacao;
        }
        if (percentual >= 100) {
            return TAREFA_CONCLUIDA;
        }
        if (situacao == TAREFA_A_FAZER && percentual > 0) {
            return TAREFA_EM_ANDAMENTO;
        }
        return situacao;
    }

    public static boolean encerrado(int situacao) {
        return situacao == CONCLUIDO || situacao == CANCELADO;
    }

    public static String rotulo(int situacao) {
        return switch (situacao) {
            case PLANEJADO -> "Planejado";
            case EM_ANDAMENTO -> "Em andamento";
            case PAUSADO -> "Pausado";
            case CONCLUIDO -> "Concluído";
            default -> "Cancelado";
        };
    }

    public static String rotuloTarefa(int situacao) {
        return switch (situacao) {
            case TAREFA_A_FAZER -> "A fazer";
            case TAREFA_EM_ANDAMENTO -> "Em andamento";
            case TAREFA_CONCLUIDA -> "Concluída";
            default -> "Cancelada";
        };
    }

    private static int feito(Tarefa t) {
        return t.situacao() == TAREFA_CONCLUIDA ? 100 : Math.max(0, Math.min(100, t.percentual()));
    }

}
