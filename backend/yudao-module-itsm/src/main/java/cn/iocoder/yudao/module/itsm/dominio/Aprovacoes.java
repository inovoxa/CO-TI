package cn.iocoder.yudao.module.itsm.dominio;

import java.util.Collection;

/**
 * Situação global das aprovações de um chamado (GLPI: CommonITILValidation::computeValidationStatus, com
 * exigência de 100% — todos os aprovadores precisam aprovar; uma recusa recusa o pedido).
 */
public final class Aprovacoes {

    public static final int NENHUMA = 1;
    public static final int AGUARDANDO = 2;
    public static final int APROVADA = 3;
    public static final int RECUSADA = 4;

    private Aprovacoes() {
    }

    public static int global(Collection<Integer> status) {
        if (status == null || status.isEmpty()) {
            return NENHUMA;
        }
        if (status.contains(RECUSADA)) {
            return RECUSADA;
        }
        return status.stream().allMatch(s -> s == APROVADA) ? APROVADA : AGUARDANDO;
    }

    public static String rotulo(int global) {
        return switch (global) {
            case AGUARDANDO -> "Aguardando aprovação";
            case APROVADA -> "Aprovado";
            case RECUSADA -> "Recusado";
            default -> "Sem aprovação";
        };
    }

}
