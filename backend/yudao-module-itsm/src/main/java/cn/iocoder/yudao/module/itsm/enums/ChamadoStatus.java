package cn.iocoder.yudao.module.itsm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Situação do chamado. Os códigos são os mesmos do GLPI (CommonITILObject::INCOMING...CLOSED), o que facilita
 * importar a base de um GLPI existente.
 */
@Getter
@AllArgsConstructor
public enum ChamadoStatus {

    NOVO(1, "Novo"),
    ATRIBUIDO(2, "Em atendimento"),
    PLANEJADO(3, "Em atendimento (planejado)"),
    PENDENTE(4, "Pendente"),
    SOLUCIONADO(5, "Solucionado"),
    FECHADO(6, "Fechado");

    private final int codigo;
    private final String rotulo;

    /** Em aberto = ainda conta na fila e no SLA. */
    public boolean aberto() {
        return this != SOLUCIONADO && this != FECHADO;
    }

    /** Alguém está trabalhando nele (atribuído ou planejado). */
    public boolean emAtendimento() {
        return this == ATRIBUIDO || this == PLANEJADO;
    }

    public static ChamadoStatus de(int codigo) {
        return Arrays.stream(values()).filter(s -> s.codigo == codigo).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Situação desconhecida: " + codigo));
    }

}
