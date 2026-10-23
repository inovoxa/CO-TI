package cn.iocoder.yudao.module.itsm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/** Mesmos códigos do GLPI (Ticket::INCIDENT_TYPE, Ticket::DEMAND_TYPE). */
@Getter
@AllArgsConstructor
public enum ChamadoTipo {

    INCIDENTE(1, "Incidente"),
    REQUISICAO(2, "Requisição");

    private final int codigo;
    private final String rotulo;

    public static ChamadoTipo de(int codigo) {
        return Arrays.stream(values()).filter(t -> t.codigo == codigo).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo desconhecido: " + codigo));
    }

}
