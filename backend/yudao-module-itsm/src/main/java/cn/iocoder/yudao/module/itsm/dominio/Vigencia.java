package cn.iocoder.yudao.module.itsm.dominio;

import java.time.LocalDate;

/**
 * Vigência de contrato: fim do período atual, prazo do aviso prévio, situação e data do alerta.
 * Sem banco e sem Spring, para ser testada à parte.
 *
 * <p>Regras inspiradas no GLPI 12 (Contract, Infocom::getWarrantyExpir e Contract::cronContract):
 * <ul>
 *   <li>o contrato vale de {@code inicio} até {@code inicio + duração - 1 dia};</li>
 *   <li>renovação tácita: ao fim de cada período começa outro do mesmo tamanho, sem ação de ninguém, até ser
 *       encerrado; o que importa é o prazo do aviso prévio do período atual (depois dele, renova de novo);</li>
 *   <li>renovação expressa: só continua se alguém registrar a renovação (a duração aumenta);</li>
 *   <li>aviso prévio: quem quiser cancelar ou renegociar precisa avisar até {@code fim + 1 dia - aviso};</li>
 *   <li>o alerta sai {@code alertaDias} antes do prazo que vier primeiro (aviso prévio, se houver, senão o fim).</li>
 * </ul>
 */
public final class Vigencia {

    public static final int NAO_RENOVA = 0;
    public static final int TACITA = 1;
    public static final int EXPRESSA = 2;

    public enum Situacao {
        FUTURO("Ainda não começou"),
        VIGENTE("Vigente"),
        A_VENCER("Perto do prazo"),
        VENCIDO("Vencido"),
        ENCERRADO("Encerrado");

        private final String rotulo;

        Situacao(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    /** Resultado do cálculo para a data de referência. */
    public record Estado(LocalDate fim, LocalDate prazoAviso, LocalDate prazo, LocalDate dataAlerta, Situacao situacao,
                         int periodo) {
    }

    private Vigencia() {
    }

    public static Estado calcular(LocalDate inicio, int duracaoMeses, int avisoMeses, int renovacao, int alertaDias,
                                  boolean encerrado, LocalDate hoje) {
        if (duracaoMeses <= 0) {
            throw new IllegalArgumentException("duração precisa ser positiva");
        }
        int periodo = 1;
        LocalDate fim = inicio.plusMonths(duracaoMeses).minusDays(1);
        if (renovacao == TACITA) {
            // avança enquanto o prazo do período já passou: dali em diante, o período seguinte é certo
            while (prazo(inicio, duracaoMeses * periodo, avisoMeses).isBefore(hoje)) {
                periodo++;
            }
            fim = inicio.plusMonths((long) duracaoMeses * periodo).minusDays(1);
        }
        LocalDate aviso = avisoMeses > 0 ? prazo(inicio, duracaoMeses * periodo, avisoMeses) : null;
        LocalDate prazo = prazo(inicio, duracaoMeses * periodo, avisoMeses);
        LocalDate alerta = prazo.minusDays(alertaDias);
        Situacao s;
        if (encerrado) {
            s = Situacao.ENCERRADO;
        } else if (hoje.isBefore(inicio)) {
            s = Situacao.FUTURO;
        } else if (hoje.isAfter(fim)) {
            s = Situacao.VENCIDO;
        } else if (!hoje.isBefore(alerta)) {
            s = Situacao.A_VENCER;
        } else {
            s = Situacao.VIGENTE;
        }
        return new Estado(fim, aviso, prazo, alerta, s, periodo);
    }

    /** Prazo do período: com aviso prévio, o último dia para avisar (dia seguinte ao fim, menos o aviso); sem, o fim. */
    private static LocalDate prazo(LocalDate inicio, int mesesAteFim, int avisoMeses) {
        LocalDate depoisDoFim = inicio.plusMonths(mesesAteFim);
        return avisoMeses > 0 ? depoisDoFim.minusMonths(avisoMeses) : depoisDoFim.minusDays(1);
    }

    public static String rotuloRenovacao(int renovacao) {
        return switch (renovacao) {
            case TACITA -> "Tácita (automática)";
            case EXPRESSA -> "Expressa (precisa renovar)";
            default -> "Não renova";
        };
    }

}
