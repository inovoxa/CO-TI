package cn.iocoder.yudao.module.itsm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/** Situação do problema. Códigos do GLPI (CommonITILObject e Problem::getAllStatusArray), na ordem de exibição. */
@Getter
@AllArgsConstructor
public enum ProblemaStatus {

    NOVO(1, "Novo"),
    ACEITO(7, "Aceito"),
    EM_ANALISE(2, "Em análise"),
    PLANEJADO(3, "Em análise (planejado)"),
    PENDENTE(4, "Pendente"),
    SOLUCIONADO(5, "Solucionado"),
    EM_OBSERVACAO(8, "Em observação"),
    FECHADO(6, "Fechado");

    private final int codigo;
    private final String rotulo;

    /** Ainda na fila de trabalho (em observação conta: alguém precisa confirmar que o problema não voltou). */
    public boolean aberto() {
        return this != SOLUCIONADO && this != FECHADO;
    }

    public static ProblemaStatus de(int codigo) {
        return Arrays.stream(values()).filter(s -> s.codigo == codigo).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Situação desconhecida: " + codigo));
    }

}
