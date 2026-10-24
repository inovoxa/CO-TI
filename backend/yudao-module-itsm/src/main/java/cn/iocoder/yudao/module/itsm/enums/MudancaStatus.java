package cn.iocoder.yudao.module.itsm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/** Situação da mudança. Códigos do GLPI (Change::getAllStatusArray), na ordem de exibição. */
@Getter
@AllArgsConstructor
public enum MudancaStatus {

    NOVA(1, "Nova"),
    EM_AVALIACAO(9, "Em avaliação"),
    EM_APROVACAO(10, "Em aprovação"),
    ACEITA(7, "Aceita"),
    PENDENTE(4, "Pendente"),
    EM_TESTE(11, "Em teste"),
    EM_QUALIFICACAO(12, "Em qualificação"),
    APLICADA(5, "Aplicada"),
    EM_REVISAO(8, "Em revisão"),
    FECHADA(6, "Fechada"),
    RECUSADA(13, "Recusada"),
    CANCELADA(14, "Cancelada");

    private final int codigo;
    private final String rotulo;

    /** Ainda na fila de trabalho (em revisão conta: a revisão pós-implantação é parte do trabalho). */
    public boolean aberta() {
        return this != APLICADA && !encerrada();
    }

    /** Não volta mais sem reabrir. */
    public boolean encerrada() {
        return this == FECHADA || this == RECUSADA || this == CANCELADA;
    }

    public static MudancaStatus de(int codigo) {
        return Arrays.stream(values()).filter(s -> s.codigo == codigo).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Situação desconhecida: " + codigo));
    }

}
