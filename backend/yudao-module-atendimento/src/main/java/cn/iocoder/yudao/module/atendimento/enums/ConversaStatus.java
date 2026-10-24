package cn.iocoder.yudao.module.atendimento.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/** Situação da conversa. Mesmos códigos do Chatwoot (Conversation#status). */
@Getter
@AllArgsConstructor
public enum ConversaStatus {

    ABERTA(0, "Aberta"),
    RESOLVIDA(1, "Resolvida"),
    /** Com o robô (ainda não chegou a uma pessoa) */
    PENDENTE(2, "Pendente"),
    ADIADA(3, "Adiada");

    private final int codigo;
    private final String rotulo;

    public static ConversaStatus de(int codigo) {
        return Arrays.stream(values()).filter(s -> s.codigo == codigo).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Situação desconhecida: " + codigo));
    }

}
