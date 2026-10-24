package cn.iocoder.yudao.module.itsm.ad;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutorAdSimuladoTest {

    private final ExecutorAdSimulado executor = new ExecutorAdSimulado();

    private Map<String, Object> rodar(String acao, String json) throws Exception {
        return JsonUtils.parseObject(executor.executar(acao, json), new TypeReference<Map<String, Object>>() {
        });
    }

    @Test
    void loginVemDosCamposDoFormulario() {
        assertEquals("maria.s", ExecutorAdSimulado.login("Reset", Map.of("login_usuario_reset", "maria.s", "login_solicitante", "chefe")));
        assertEquals("chefe", ExecutorAdSimulado.login("AcessoPasta", Map.of("login_solicitante", "chefe")));
        assertEquals("joao.souza", ExecutorAdSimulado.login("Criar", Map.of("nome_completo", "João da Silva Souza", "login_solicitante", "chefe")));
        assertEquals("jsouza", ExecutorAdSimulado.login("CriarEmail", Map.of("nome_usuario", "jsouza")));
    }

    @Test
    void respondeComoOScript() throws Exception {
        Map<String, Object> r = rodar("Reset", "{\"login_usuario_reset\":\"maria.s\"}");
        assertEquals(true, r.get("sucesso"));
        assertEquals("reset_senha", r.get("acao_real"));
        assertTrue(String.valueOf(r.get("senha")).startsWith("Co#"));

        r = rodar("Desativar", "{\"login_usuario_desativar\":\"maria.s\"}");
        assertNull(r.get("senha"), "desativar não gera senha");

        r = rodar("Reset", "{\"login_usuario_reset\":\"erro.simulado\"}");
        assertEquals(false, r.get("sucesso"));
        assertNotNull(r.get("erro"));

        assertThrows(java.io.IOException.class, () -> executor.executar("Reset", "{\"login_usuario_reset\":\"falha.infra\"}"));
    }

}
