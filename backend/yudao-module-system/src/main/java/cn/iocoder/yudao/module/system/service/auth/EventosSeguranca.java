package cn.iocoder.yudao.module.system.service.auth;

import java.util.Map;

/**
 * CO-TI: eventos que vão para a trilha de auditoria encadeada (implementada pelo módulo identidade).
 * A implementação nunca pode derrubar a operação que a chamou.
 */
public interface EventosSeguranca {

    String LOGIN_SUCESSO = "login.sucesso";
    String LOGIN_FALHA = "login.falha";
    String LOGIN_MFA_PENDENTE = "login.mfa_pendente";
    String LOGIN_MFA_FALHA = "login.mfa_falha";
    String LOGOUT = "logout";

    void registrar(String acao, Long userId, String usuario, Map<String, Object> detalhes);

}
