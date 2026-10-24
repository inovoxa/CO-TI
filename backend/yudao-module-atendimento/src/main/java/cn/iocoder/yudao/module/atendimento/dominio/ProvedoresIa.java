package cn.iocoder.yudao.module.atendimento.dominio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tipos de provedor de IA. Todos falam a API de chat da OpenAI ({@code POST {base}/chat/completions}, com tools):
 * os comerciais têm um endpoint compatível; o tipo só preenche o endereço padrão, diz se a chave é obrigatória e
 * acrescenta os cabeçalhos próprios (OpenRouter identifica o aplicativo).
 */
public final class ProvedoresIa {

    private ProvedoresIa() {
    }

    public record Tipo(String codigo, String rotulo, String baseUrl, boolean exigeChave, String exemploModelo) {
    }

    public static final Map<String, Tipo> TIPOS = new LinkedHashMap<>();

    static {
        for (Tipo t : new Tipo[]{
                new Tipo("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", true, "deepseek/deepseek-chat"),
                new Tipo("openai", "OpenAI", "https://api.openai.com/v1", true, "gpt-4o-mini"),
                new Tipo("anthropic", "Anthropic (Claude)", "https://api.anthropic.com/v1", true, "claude-sonnet-5-5"),
                new Tipo("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", true, "gemini-2.5-flash"),
                new Tipo("groq", "Groq", "https://api.groq.com/openai/v1", true, "llama-3.3-70b-versatile"),
                new Tipo("deepseek", "DeepSeek", "https://api.deepseek.com/v1", true, "deepseek-chat"),
                new Tipo("mistral", "Mistral", "https://api.mistral.ai/v1", true, "mistral-small-latest"),
                new Tipo("ollama", "Ollama (local)", "http://ollama:11434/v1", false, "llama3.1"),
                new Tipo("compativel", "Outro compatível com OpenAI (vLLM, LM Studio, proxy)", "", false, "")}) {
            TIPOS.put(t.codigo(), t);
        }
    }

    /** Cabeçalhos extras do tipo (o OpenRouter usa para identificar o aplicativo nas estatísticas dele). */
    public static Map<String, String> cabecalhos(String tipo, String urlPublica) {
        if ("openrouter".equals(tipo)) {
            Map<String, String> h = new LinkedHashMap<>();
            if (urlPublica != null && !urlPublica.isBlank()) {
                h.put("HTTP-Referer", urlPublica);
            }
            h.put("X-Title", "CO-TI");
            return h;
        }
        return Map.of();
    }

}
