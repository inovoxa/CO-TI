package cn.iocoder.yudao.module.itsm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Tipo de mudança (ITIL). O GLPI não tem esse campo; ele decide se a mudança precisa de aprovação antes de ser
 * testada ou aplicada.
 */
@Getter
@AllArgsConstructor
public enum MudancaTipo {

    /** Rotineira e de baixo risco, já aprovada de antemão (ex.: trocar toner, criar usuário). */
    PADRAO(1, "Padrão"),
    /** Passa pela aprovação antes de ser testada ou aplicada. */
    NORMAL(2, "Normal"),
    /** Precisa ser feita já (ex.: corrigir uma falha grave); a aprovação pode vir depois. */
    EMERGENCIAL(3, "Emergencial");

    private final int codigo;
    private final String rotulo;

    public boolean exigeAprovacaoAntes() {
        return this == NORMAL;
    }

    public static MudancaTipo de(int codigo) {
        return Arrays.stream(values()).filter(t -> t.codigo == codigo).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo desconhecido: " + codigo));
    }

}
