package cn.iocoder.yudao.module.atendimento.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Regras das APIs da Meta (WhatsApp Cloud API e Instagram) sem dependência de servidor: assinatura dos webhooks,
 * classificação dos erros e a janela de 24 horas.
 */
public final class Meta {

    private Meta() {
    }

    /** Fora da janela de 24 h (WhatsApp 131047; Instagram 10/2018278) */
    public static final String MSG_JANELA = "Passaram 24 horas desde a última mensagem do contato: só é possível mandar um modelo aprovado";

    /** Erros passageiros: limite de requisições, instabilidade do provedor. */
    private static final Set<Integer> PASSAGEIROS = Set.of(1, 2, 4, 17, 32, 341, 613, 80007, 130429, 131000, 131048, 131056, 133004);

    /**
     * Confere o cabeçalho {@code X-Hub-Signature-256: sha256=<hex>} (HMAC-SHA256 do corpo com a chave secreta do app),
     * em tempo constante.
     */
    public static boolean assinaturaValida(String appSecret, byte[] corpo, String cabecalho) {
        if (appSecret == null || appSecret.isEmpty() || cabecalho == null) {
            return false;
        }
        String recebida = cabecalho.trim().toLowerCase(Locale.ROOT);
        if (!recebida.startsWith("sha256=")) {
            return false;
        }
        String esperada = "sha256=" + Webhooks.hmacSha256(appSecret, corpo);
        return MessageDigest.isEqual(esperada.getBytes(StandardCharsets.US_ASCII), recebida.getBytes(StandardCharsets.US_ASCII));
    }

    /** Comparação de segredos curtos (verify token, secret token do Telegram) em tempo constante. */
    public static boolean iguais(String a, String b) {
        if (a == null || b == null || a.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Classificação de um erro devolvido pela Meta. */
    public record Erro(boolean passageiro, String mensagem) {
    }

    /**
     * @param status código HTTP
     * @param json   corpo da resposta ({@code {"error": {"code", "error_subcode", "message", "error_data": {"details"}}}})
     */
    @SuppressWarnings("unchecked")
    public static Erro classificar(int status, Map<String, Object> json) {
        Map<String, Object> e = json != null && json.get("error") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        int codigo = numero(e.get("code"));
        int sub = numero(e.get("error_subcode"));
        String msg = e.get("message") instanceof String s ? s : "HTTP " + status;
        if (e.get("error_data") instanceof Map<?, ?> d && d.get("details") instanceof String det && !det.isBlank()) {
            msg = msg + ": " + det;
        }
        if (codigo == 131047 || (codigo == 10 && sub == 2018278) || sub == 2534022) {
            return new Erro(false, MSG_JANELA);
        }
        if (codigo == 190) {
            return new Erro(false, "Token de acesso inválido ou expirado: atualize na configuração da caixa (" + msg + ")");
        }
        if (codigo == 131026) {
            return new Erro(false, "Mensagem não entregue: o número não tem WhatsApp ou não aceitou os termos atuais (" + msg + ")");
        }
        if (status >= 500 || status == 429 || PASSAGEIROS.contains(codigo)) {
            return new Erro(true, "Meta: " + msg + (codigo != 0 ? " (código " + codigo + ")" : ""));
        }
        return new Erro(false, "Meta recusou: " + msg + (codigo != 0 ? " (código " + codigo + ")" : ""));
    }

    /** Situação de entrega do WhatsApp para a do CO-TI. */
    public static String situacao(String status) {
        return switch (status == null ? "" : status) {
            case "sent" -> "enviada";
            case "delivered" -> "entregue";
            case "read", "played" -> "lida";
            case "failed" -> "falhou";
            default -> null;
        };
    }

    /** Tipo de mídia do WhatsApp para o tipo do arquivo (o que o WhatsApp não mostra como imagem/vídeo/áudio vai como documento). */
    public static String tipoMidiaWhatsapp(String mime) {
        String t = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (t.equals("image/jpeg") || t.equals("image/png")) {
            return "image";
        }
        if (t.equals("video/mp4") || t.equals("video/3gpp")) {
            return "video";
        }
        if (Set.of("audio/aac", "audio/mp4", "audio/mpeg", "audio/amr", "audio/ogg").contains(t)) {
            return "audio";
        }
        return "document";
    }

    /** Formato da mensagem no CO-TI pelo tipo do arquivo. */
    public static String formato(String mime) {
        String t = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        return t.startsWith("image/") ? "imagem" : t.startsWith("audio/") ? "audio" : t.startsWith("video/") ? "video" : "arquivo";
    }

    /** Extensão para dar nome a um arquivo que veio sem nome. */
    public static String extensao(String mime) {
        String t = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        int pv = t.indexOf(';');
        if (pv > 0) {
            t = t.substring(0, pv).trim();
        }
        return switch (t) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/3gpp" -> ".3gp";
            case "audio/ogg" -> ".ogg";
            case "audio/mpeg" -> ".mp3";
            case "audio/mp4", "audio/aac" -> ".m4a";
            case "audio/amr" -> ".amr";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private static int numero(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? 0 : Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
