package cn.iocoder.yudao.module.identidade.ad;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

/**
 * Conta de usuário lida do AD, já normalizada.
 */
@Value
@Builder
public class AdUsuario {

    /** objectGUID em texto (estável mesmo se a conta for renomeada ou movida de OU). */
    String guid;
    String login;
    String dn;
    String nome;
    String email;
    String telefone;
    String cargo;
    /** DN do gestor (atributo manager), usado nas aprovações do ITSM. */
    String gestorDn;
    /** Containers da raiz configurada até a conta, do mais alto para o mais baixo. Vazio = direto na raiz. */
    List<String> caminhoOus;
    /** DNs dos grupos (diretos e, no AD real, também os aninhados). */
    Set<String> grupos;
    boolean ativo;

}
