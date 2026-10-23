package cn.iocoder.yudao.module.itsm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/** Papel de uma pessoa ou equipe no chamado. Mesmos códigos do GLPI (CommonITILActor::REQUESTER...OBSERVER). */
@Getter
@AllArgsConstructor
public enum AtorTipo {

    SOLICITANTE(1, "Solicitante"),
    TECNICO(2, "Técnico"),
    OBSERVADOR(3, "Observador");

    private final int codigo;
    private final String rotulo;

    public static AtorTipo de(int codigo) {
        return Arrays.stream(values()).filter(t -> t.codigo == codigo).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Papel desconhecido: " + codigo));
    }

}
