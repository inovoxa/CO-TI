package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.MONITOR_SEM_CHAVE_MESTRA;

/**
 * Senhas dos monitores e dos servidores Docker: guardadas cifradas com a chave-mestra, decifradas só na hora de
 * verificar. Nunca voltam para a tela; ela recebe apenas se estão definidas.
 */
@Component
public class SegredosMonitor {

    public static final String SENHA = "senha", SEGREDO = "segredo";
    static final String CONTEXTO = "monitoramento-monitor";
    static final String CONTEXTO_DOCKER = "monitoramento-docker";

    @Resource
    private CifradorSegredos cifradorSegredos;

    /** Junta os valores novos (os vazios mantêm o guardado) e devolve o texto cifrado, ou nulo se não sobrar nenhum. */
    public String juntar(String guardado, String senha, String segredo) {
        boolean novaSenha = senha != null && !senha.isEmpty();
        boolean novoSegredo = segredo != null && !segredo.isEmpty();
        if (!novaSenha && !novoSegredo) {
            return guardado;
        }
        if (!cifradorSegredos.configurado()) {
            throw exception(MONITOR_SEM_CHAVE_MESTRA);
        }
        Map<String, String> valores = new LinkedHashMap<>(ler(guardado));
        if (novaSenha) {
            valores.put(SENHA, senha);
        }
        if (novoSegredo) {
            valores.put(SEGREDO, segredo);
        }
        return cifradorSegredos.cifrar(JsonUtils.toJsonString(valores), CONTEXTO);
    }

    public Map<String, String> ler(String guardado) {
        if (guardado == null || guardado.isBlank()) {
            return Map.of();
        }
        Map<String, String> m = JsonUtils.parseObject(cifradorSegredos.decifrar(guardado, CONTEXTO), new TypeReference<>() {
        });
        return m == null ? Map.of() : m;
    }

    public String chaveDocker(String guardada, String nova) {
        if (nova == null || nova.isBlank()) {
            return guardada;
        }
        if (!cifradorSegredos.configurado()) {
            throw exception(MONITOR_SEM_CHAVE_MESTRA);
        }
        return cifradorSegredos.cifrar(nova.trim(), CONTEXTO_DOCKER);
    }

    public String lerChaveDocker(String guardada) {
        return guardada == null || guardada.isBlank() ? null : cifradorSegredos.decifrar(guardada, CONTEXTO_DOCKER);
    }

}
