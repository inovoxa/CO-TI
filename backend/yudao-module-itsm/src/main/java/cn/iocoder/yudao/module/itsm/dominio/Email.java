package cn.iocoder.yudao.module.itsm.dominio;

import org.springframework.web.util.HtmlUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras de e-mail sem rede nem banco: modelos de notificação e leitura de mensagens recebidas
 * (GLPI: NotificationTemplate e MailCollector).
 */
public final class Email {

    /** Linha posta no topo das notificações; na resposta, o que vem depois dela é o e-mail citado. */
    public static final String MARCA_RESPOSTA = "##- Responda acima desta linha -##";

    // [CO-TI #123], [Chamado #123], [#123] — o número entre colchetes depois de "#"
    private static final Pattern NUMERO_ASSUNTO = Pattern.compile("\\[[^\\]#]{0,40}#\\s*(\\d{1,12})\\s*]");
    // Message-ID das notificações: <coti.chamado.123@dominio> e <coti.123.456.x@dominio>
    private static final Pattern NUMERO_REFERENCIA = Pattern.compile("<coti\\.(?:chamado\\.)?(\\d{1,12})[.@]");
    private static final Pattern VARIAVEL = Pattern.compile("\\{\\{\\{\\s*([a-z.]+)\\s*}}}|\\{\\{\\s*([a-z.]+)\\s*}}");

    private Email() {
    }

    /**
     * Monta o texto do modelo: {{x}} entra escapado; {{{x}}} entra como está (só para HTML já saneado).
     * Variável desconhecida vira vazio.
     */
    public static String preencher(String modelo, Map<String, String> valores) {
        if (modelo == null) {
            return "";
        }
        Matcher m = VARIAVEL.matcher(modelo);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v;
            if (m.group(1) != null) {
                v = valores.getOrDefault(m.group(1), "");
            } else {
                v = HtmlUtils.htmlEscape(valores.getOrDefault(m.group(2), ""));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Assunto: texto puro numa linha (sem escape HTML, sem quebras que abririam cabeçalhos). */
    public static String preencherAssunto(String modelo, Map<String, String> valores) {
        String s = HtmlUtils.htmlUnescape(preencher(modelo, valores)).replaceAll("[\\r\\n\\t]+", " ").trim();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    /** Número do chamado citado na resposta: pelo assunto ou pelos cabeçalhos In-Reply-To/References. */
    public static Optional<Long> chamadoCitado(String assunto, String inReplyTo, String references) {
        if (assunto != null) {
            Matcher m = NUMERO_ASSUNTO.matcher(assunto);
            if (m.find()) {
                return Optional.of(Long.parseLong(m.group(1)));
            }
        }
        for (String h : new String[]{inReplyTo, references}) {
            if (h != null) {
                Matcher m = NUMERO_REFERENCIA.matcher(h);
                if (m.find()) {
                    return Optional.of(Long.parseLong(m.group(1)));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resposta automática, lista ou aviso de entrega: não vira chamado (evita laço com férias e robôs).
     *
     * @param cabecalhos nomes em minúsculas
     */
    public static Optional<String> automatico(Map<String, String> cabecalhos, String remetente) {
        String auto = cabecalhos.getOrDefault("auto-submitted", "").toLowerCase(Locale.ROOT).trim();
        if (!auto.isEmpty() && !auto.equals("no")) {
            return Optional.of("resposta automática (Auto-Submitted: " + auto + ")");
        }
        String prec = cabecalhos.getOrDefault("precedence", "").toLowerCase(Locale.ROOT).trim();
        if (prec.equals("bulk") || prec.equals("junk") || prec.equals("list") || prec.equals("auto_reply")) {
            return Optional.of("mensagem em massa (Precedence: " + prec + ")");
        }
        if (cabecalhos.containsKey("x-autoreply") || cabecalhos.containsKey("x-autorespond")) {
            return Optional.of("resposta automática");
        }
        if (cabecalhos.containsKey("x-co-ti-chamado")) {
            return Optional.of("notificação do próprio CO-TI");
        }
        if (cabecalhos.containsKey("list-id") || cabecalhos.containsKey("list-unsubscribe")) {
            return Optional.of("lista de e-mails");
        }
        String r = remetente == null ? "" : remetente.toLowerCase(Locale.ROOT);
        if (r.startsWith("mailer-daemon@") || r.startsWith("postmaster@") || r.startsWith("no-reply@")
                || r.startsWith("noreply@")) {
            return Optional.of("remetente automático (" + r + ")");
        }
        return Optional.empty();
    }

    /** O servidor de entrada confirmou o remetente (DMARC ou DKIM aprovado em Authentication-Results). */
    public static boolean remetenteAutenticado(String authenticationResults) {
        if (authenticationResults == null) {
            return false;
        }
        String a = authenticationResults.toLowerCase(Locale.ROOT);
        return a.contains("dmarc=pass") || a.contains("dkim=pass");
    }

    /** Remove o e-mail citado: tudo a partir da marca das notificações (quando presente). */
    public static String semCitacao(String html) {
        if (html == null) {
            return "";
        }
        int i = html.indexOf(MARCA_RESPOSTA);
        if (i < 0) {
            i = html.indexOf(HtmlUtils.htmlEscape(MARCA_RESPOSTA));
        }
        return i < 0 ? html : html.substring(0, i);
    }

    /** Texto puro do e-mail como HTML (escapado, com quebras de linha). */
    public static String textoParaHtml(String texto) {
        return texto == null ? "" : "<p>" + HtmlUtils.htmlEscape(texto).replace("\r\n", "\n").replace("\n", "<br>") + "</p>";
    }

    /** Assunto sem "Re:", "Enc:", "Fwd:" repetidos, para virar título. */
    public static String titulo(String assunto) {
        if (assunto == null || assunto.isBlank()) {
            return "(sem assunto)";
        }
        String t = assunto.replaceAll("(?i)^\\s*((re|res|enc|fw|fwd|tr)\\s*:\\s*)+", "").trim();
        if (t.isEmpty()) {
            t = "(sem assunto)";
        }
        return t.length() > 255 ? t.substring(0, 255) : t;
    }

}
