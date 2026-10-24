package cn.iocoder.yudao.module.itsm.ad;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Executor de demonstração e teste: não toca no AD, responde como o script responderia.
 * Um login "erro.simulado" ou "falha.infra" força os caminhos de erro (de negócio e de infraestrutura).
 */
public class ExecutorAdSimulado implements ExecutorAd {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String executar(String acao, String dadosJson) throws Exception {
        Map<String, Object> dados = JsonUtils.parseObject(dadosJson, new TypeReference<Map<String, Object>>() {
        });
        String login = login(acao, dados);
        if ("falha.infra".equals(login)) {
            throw new java.io.IOException("Servidor do AD inacessível (simulado)");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        if ("erro.simulado".equals(login)) {
            r.put("sucesso", false);
            r.put("erro", "Usuário 'erro.simulado' não encontrado no AD (simulado)");
            return JsonUtils.toJsonString(r);
        }
        r.put("sucesso", true);
        r.put("login", login);
        switch (acao) {
            case "Criar", "Reset", "Reativar", "Transferir" -> {
                r.put("acao_real", switch (acao) {
                    case "Criar" -> "criado";
                    case "Reset" -> "reset_senha";
                    case "Reativar" -> "reativado";
                    default -> "transferido";
                });
                r.put("senha", senha());
                r.put("mensagem", "Operação " + acao + " concluída para " + login + " (simulado)");
            }
            case "CriarEmail" -> {
                r.put("acao_real", "email_criado");
                r.put("email", login + "@exemplo.local");
                r.put("mensagem", "E-mail criado para " + login + " (simulado)");
            }
            default -> {
                r.put("acao_real", acao.toLowerCase(Locale.ROOT));
                r.put("mensagem", "Operação " + acao + " concluída para " + login + " (simulado)");
            }
        }
        return JsonUtils.toJsonString(r);
    }

    @Override
    public String modo() {
        return "simulado";
    }

    /** Mesmos campos que o script lê: login_usuario_reset, login_usuario_reativar... ou o nome completo (criação). */
    static String login(String acao, Map<String, Object> dados) {
        // AcessoPasta: a pasta é liberada para login_solicitante (pergunta do formulário ou a conta de quem pediu)
        String login = texto(dados.get("AcessoPasta".equals(acao) ? "login_solicitante" : "login"));
        for (String campo : dados.keySet()) {
            if (login.isBlank() && campo.startsWith("login_usuario")) {
                login = texto(dados.get(campo));
            }
        }
        if (login.isBlank()) {
            login = texto(dados.get("nome_usuario"));
        }
        return login.isBlank() ? loginDe(texto(dados.get("nome_completo"))) : login;
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    static String loginDe(String nome) {
        String sem = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String[] p = sem.split("\\s+");
        return p.length >= 2 ? p[0] + "." + p[p.length - 1] : (p[0].isBlank() ? "usuario" : p[0]);
    }

    private static String senha() {
        String a = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder("Co#");
        for (int i = 0; i < 11; i++) {
            sb.append(a.charAt(RANDOM.nextInt(a.length())));
        }
        return sb.toString();
    }

}
