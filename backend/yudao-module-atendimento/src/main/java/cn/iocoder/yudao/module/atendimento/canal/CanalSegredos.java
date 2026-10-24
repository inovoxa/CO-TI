package cn.iocoder.yudao.module.atendimento.canal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuração do canal guardada em {@code caixa.config} (JSON). Tokens e chaves ficam cifrados com a chave-mestra
 * (contexto {@value #CONTEXTO}); só o servidor os decifra, na hora de falar com o provedor.
 */
@Component
public class CanalSegredos {

    public static final String CONTEXTO = "atendimento-canal";

    /** Campos cifrados dentro do JSON */
    public static final String[] SEGREDOS = {"token", "appSecret", "botToken", "secretToken"};

    @Resource
    private CifradorSegredos cifrador;

    /** Configuração já decifrada. */
    public record Config(String phoneNumberId, String wabaId, boolean coexistencia, String igUserId, String token,
                         String appSecret, String botToken, String verifyToken, String secretToken, Map<String, Object> info) {

        /** Número exibido da empresa (só dígitos), quando o teste de conexão já o trouxe */
        public String numeroEmpresa() {
            Object n = info == null ? null : info.get("display_phone_number");
            return n == null ? null : n.toString().replaceAll("\\D", "");
        }
    }

    public Map<String, Object> bruto(CaixaDO caixa) {
        if (caixa.getConfig() == null || caixa.getConfig().isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> m = JsonUtils.parseObject(caixa.getConfig(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
        return m == null ? new LinkedHashMap<>() : m;
    }

    @SuppressWarnings("unchecked")
    public Config ler(CaixaDO caixa) {
        Map<String, Object> m = bruto(caixa);
        return new Config(texto(m, "phoneNumberId"), texto(m, "wabaId"), Boolean.TRUE.equals(m.get("coexistencia")),
                texto(m, "igUserId"), segredo(m, "token"), segredo(m, "appSecret"), segredo(m, "botToken"),
                texto(m, "verifyToken"), segredo(m, "secretToken"),
                m.get("info") instanceof Map<?, ?> i ? (Map<String, Object>) i : Map.of());
    }

    public String cifrar(String texto) {
        return cifrador.cifrar(texto, CONTEXTO);
    }

    public boolean configurado() {
        return cifrador.configurado();
    }

    private String segredo(Map<String, Object> m, String campo) {
        String c = texto(m, campo);
        return c == null ? null : cifrador.decifrar(c, CONTEXTO);
    }

    private static String texto(Map<String, Object> m, String campo) {
        Object v = m.get(campo);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

}
