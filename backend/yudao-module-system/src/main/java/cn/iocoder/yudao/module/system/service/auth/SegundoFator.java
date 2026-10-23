package cn.iocoder.yudao.module.system.service.auth;

/**
 * CO-TI: segundo fator do login (implementado pelo módulo identidade, com TOTP).
 * Se existir o bean, todo caminho que emite token passa por ele.
 */
public interface SegundoFator {

    /** O usuário cadastrou e ativou o segundo fator. */
    boolean exige(Long userId);

    /** Confere o código (TOTP ou código de recuperação). Cada código vale uma vez só. */
    boolean verificar(Long userId, String codigo);

}
