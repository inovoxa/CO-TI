package cn.iocoder.yudao.module.itsm.dominio;

import org.jsoup.parser.Parser;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversões de dados do GLPI 10/11 para o CO-TI. Sem banco e sem Spring, para ser testadas à parte.
 *
 * <p>Os códigos de situação, papéis (solicitante/técnico/observador), estados de tarefa e de aprovação são os mesmos
 * nos dois sistemas; aqui ficam só as diferenças: o texto (o GLPI 10 grava o HTML com as entidades escapadas, o 11
 * grava o HTML puro), as escalas fora da faixa e as imagens que apontam para o document.send.php do GLPI.
 */
public final class GlpiConverte {

    /** {@code document.send.php?docid=12} (com ou sem outros parâmetros e com &amp;amp;) */
    private static final Pattern DOCUMENTO = Pattern.compile("document\\.send\\.php\\?(?:[^\"'>]*?(?:&amp;|&))?docid=(\\d+)[^\"'>]*");
    /** Atributo inteiro (src ou href) que aponta para um documento do GLPI, com o caminho antes dele */
    private static final Pattern ATRIBUTO = Pattern.compile("(src|href)\\s*=\\s*([\"'])([^\"']*document\\.send\\.php[^\"']*)\\2",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_DOCUMENTO = Pattern.compile("<img\\b[^>]*?src=[\"'][^\"']*document\\.send\\.php[^\"']*[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);

    private GlpiConverte() {
    }

    /**
     * HTML do GLPI pronto para o saneador: desfaz o escape do GLPI 10 ({@code &lt;p&gt;}) quando o texto não tem
     * nenhuma marcação de verdade. O resultado ainda passa por {@link HtmlSeguro#limpar}.
     */
    public static String html(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return "";
        }
        if (bruto.indexOf('<') < 0 && bruto.contains("&lt;")) {
            return Parser.unescapeEntities(bruto, false);
        }
        return bruto;
    }

    /** Números de documento citados em imagens e links do texto. */
    public static Set<Long> documentos(String html) {
        Set<Long> ids = new LinkedHashSet<>();
        if (html == null) {
            return ids;
        }
        Matcher m = DOCUMENTO.matcher(html);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return ids;
    }

    /**
     * Troca os endereços do GLPI pelos arquivos já copiados. Imagem de documento que não veio vira um aviso em texto
     * (senão ficaria quebrada apontando para um servidor que vai ser desligado); link sem arquivo perde o destino.
     *
     * @param url documento → endereço no CO-TI, ou null se não foi copiado
     */
    public static String trocarDocumentos(String html, Function<Long, String> url) {
        if (html == null || !html.contains("document.send.php")) {
            return html;
        }
        StringBuilder sb = new StringBuilder();
        Matcher img = IMG_DOCUMENTO.matcher(html);
        while (img.find()) {
            Set<Long> ids = documentos(img.group());
            Long id = ids.isEmpty() ? null : ids.iterator().next();
            String destino = id == null ? null : url.apply(id);
            img.appendReplacement(sb, Matcher.quoteReplacement(destino != null ? img.group()
                    : "<em>[imagem do GLPI" + (id == null ? "" : " — documento " + id) + " não importada]</em>"));
        }
        img.appendTail(sb);
        Matcher a = ATRIBUTO.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (a.find()) {
            Set<Long> ids = documentos(a.group(3));
            String destino = ids.isEmpty() ? null : url.apply(ids.iterator().next());
            a.appendReplacement(out, Matcher.quoteReplacement(a.group(1) + "=" + a.group(2) + (destino != null ? destino : "#") + a.group(2)));
        }
        a.appendTail(out);
        return out.toString();
    }

    /** Urgência e impacto: 1..5 (o GLPI aceita 0 em importações antigas). */
    public static int escala(Integer v) {
        return v == null ? 3 : Math.max(1, Math.min(5, v));
    }

    /** Prioridade: 1..6 (6 = crítica). */
    public static int prioridade(Integer v) {
        return v == null ? 3 : Math.max(1, Math.min(6, v));
    }

    public static int statusChamado(Integer v) {
        return v == null || v < 1 || v > 6 ? 1 : v;
    }

    /** Problema: códigos 1..8 (sem os de mudança). */
    public static int statusProblema(Integer v) {
        return v == null || v < 1 || v > 8 ? 1 : v;
    }

    /** Mudança: 1..14, exceto 2 e 3, que o GLPI não usa em mudança. */
    public static int statusMudanca(Integer v) {
        if (v == null || v < 1 || v > 14) {
            return 1;
        }
        return v == 2 || v == 3 ? 9 : v;
    }

    /** Aprovação (situação global ou pedido): 1 nenhuma, 2 aguardando, 3 aprovada, 4 recusada. */
    public static int validacao(Integer v) {
        return v == null || v < 1 || v > 4 ? 1 : v;
    }

    /** Estado da tarefa: 0 informativa, 1 a fazer, 2 feita. */
    public static int estadoTarefa(Integer v) {
        return v == null || v < 0 || v > 2 ? 1 : v;
    }

    /** Nota de 1 a 5; o GLPI guarda 0..5 ou, se configurado, outra escala já convertida em scaled_to_5. */
    public static Integer nota(Integer satisfacao, Double escalada) {
        Double n = escalada != null && escalada > 0 ? escalada : satisfacao == null ? null : satisfacao.doubleValue();
        if (n == null || n <= 0) {
            return null;
        }
        return (int) Math.max(1, Math.min(5, Math.round(n)));
    }

    /** "Nome Sobrenome" como o GLPI mostra; sem nome, o login. */
    public static String nome(String firstname, String realname, String login) {
        String n = ((firstname == null ? "" : firstname.trim()) + " " + (realname == null ? "" : realname.trim())).trim();
        return n.isEmpty() ? login : n;
    }

    /** Login do GLPI como usuário do CO-TI (o yudao aceita letras, números, ponto, hífen e sublinhado; até 30). */
    public static String login(String glpi) {
        if (glpi == null) {
            return null;
        }
        String s = glpi.trim().toLowerCase(Locale.ROOT);
        int arroba = s.indexOf('@');
        if (arroba > 0) {
            s = s.substring(0, arroba);   // "joao@empresa" (login por e-mail) → "joao"
        }
        s = s.replaceAll("[^a-z0-9._-]", "");
        return s.length() < 4 ? null : s.length() > 30 ? s.substring(0, 30) : s;
    }

}
