package cn.iocoder.yudao.module.itsm.dominio;

import java.util.Set;

/**
 * Regras das movimentações do estoque, sem banco.
 *
 * <p>Por unidade, o item muda de situação: disponível → em uso → disponível, com passagem pela manutenção; a baixa é
 * final. Por quantidade, o saldo sobe e desce e o item continua disponível.
 */
public final class Estoque {

    public static final String UNIDADE = "unidade";
    public static final String QUANTIDADE = "quantidade";

    public static final String DISPONIVEL = "disponivel";
    public static final String EM_USO = "em_uso";
    public static final String MANUTENCAO = "manutencao";
    public static final String BAIXADO = "baixado";

    public static final String ENTRADA = "entrada";
    public static final String RETIRADA = "retirada";
    public static final String DEVOLUCAO = "devolucao";
    public static final String MANUTENCAO_MOV = "manutencao";
    public static final String RETORNO = "retorno";
    public static final String BAIXA = "baixa";

    public static final Set<String> TIPOS_MOVIMENTO = Set.of(ENTRADA, RETIRADA, DEVOLUCAO, MANUTENCAO_MOV, RETORNO, BAIXA);

    /** Situação e saldo depois do movimento. */
    public record Novo(String situacao, int quantidade) {
    }

    private Estoque() {
    }

    /** @return o erro, ou null se o movimento é permitido */
    public static String erro(String controle, String situacao, int saldo, String movimento, int quantidade, String pessoa, String motivo) {
        if (!TIPOS_MOVIMENTO.contains(movimento)) {
            return "Movimento desconhecido";
        }
        if (BAIXADO.equals(situacao)) {
            return "Item baixado não pode ser movimentado";
        }
        if ((RETIRADA.equals(movimento) || (DEVOLUCAO.equals(movimento) && QUANTIDADE.equals(controle))) && vazio(pessoa)) {
            return "Informe quem está retirando ou devolvendo";
        }
        if ((BAIXA.equals(movimento) || MANUTENCAO_MOV.equals(movimento)) && vazio(motivo)) {
            return "Informe o motivo";
        }
        if (quantidade < 1) {
            return "A quantidade precisa ser maior que zero";
        }
        if (QUANTIDADE.equals(controle)) {
            return switch (movimento) {
                case ENTRADA, DEVOLUCAO -> null;
                case RETIRADA, BAIXA -> quantidade > saldo ? "Saldo insuficiente: há " + saldo + " em estoque" : null;
                default -> "Item controlado por quantidade não vai para manutenção";
            };
        }
        if (quantidade != 1) {
            return "Item controlado por unidade movimenta uma peça por vez";
        }
        boolean ok = switch (movimento) {
            case RETIRADA -> DISPONIVEL.equals(situacao);
            case DEVOLUCAO -> EM_USO.equals(situacao);
            case MANUTENCAO_MOV -> DISPONIVEL.equals(situacao) || EM_USO.equals(situacao);
            case RETORNO -> MANUTENCAO.equals(situacao);
            case BAIXA -> true;
            default -> false; // a entrada de uma peça é o próprio cadastro
        };
        return ok ? null : "Esse movimento não vale para um item " + rotulo(situacao);
    }

    public static Novo aplicar(String controle, String situacao, int saldo, String movimento, int quantidade) {
        if (QUANTIDADE.equals(controle)) {
            boolean soma = ENTRADA.equals(movimento) || DEVOLUCAO.equals(movimento);
            return new Novo(DISPONIVEL, soma ? saldo + quantidade : saldo - quantidade);
        }
        return switch (movimento) {
            case RETIRADA -> new Novo(EM_USO, 1);
            case DEVOLUCAO, RETORNO -> new Novo(DISPONIVEL, 1);
            case MANUTENCAO_MOV -> new Novo(MANUTENCAO, 1);
            case BAIXA -> new Novo(BAIXADO, 0);
            default -> new Novo(situacao, saldo);
        };
    }

    private static String rotulo(String situacao) {
        return switch (situacao) {
            case DISPONIVEL -> "disponível";
            case EM_USO -> "em uso";
            case MANUTENCAO -> "em manutenção";
            default -> situacao;
        };
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

}
