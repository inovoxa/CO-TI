package cn.iocoder.yudao.module.itsm.dominio;

import org.springframework.web.util.HtmlUtils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Busca em texto da base de conhecimento (PostgreSQL full-text, dicionário português, sem acento).
 *
 * <p>O texto digitado nunca vai cru para o to_tsquery (que tem sintaxe própria e daria erro com "&", "!", ":"):
 * as palavras são extraídas aqui e cada uma vira um prefixo ("impres" acha "impressora").
 */
public final class BuscaTexto {

    /** Marcadores do ts_headline: não aparecem em texto comum e são trocados por <mark> depois de escapar. */
    public static final String INICIO = "\u0002";
    public static final String FIM = "\u0003";
    private static final int MAX_PALAVRAS = 12;

    private BuscaTexto() {
    }

    /**
     * Consulta para to_tsquery('portuguese', ...): palavras com 2+ letras, sem acento, unidas por E e com prefixo.
     *
     * @return null quando não há palavra aproveitável
     */
    public static String consulta(String digitado) {
        if (digitado == null) {
            return null;
        }
        String sem = Normalizer.normalize(digitado, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String c = Arrays.stream(sem.split("[^\\p{L}\\p{N}]+"))
                .filter(p -> p.length() >= 2)
                .distinct()
                .limit(MAX_PALAVRAS)
                .map(p -> p + ":*")
                .collect(Collectors.joining(" & "));
        return c.isEmpty() ? null : c;
    }

    /** Mesma consulta, mas qualquer palavra basta (sugestões a partir do título do chamado). */
    public static String consultaQualquer(String digitado) {
        String c = consulta(digitado);
        return c == null ? null : c.replace(" & ", " | ");
    }

    /** Trecho devolvido pelo ts_headline (texto puro com marcadores) pronto para HTML: escapa e destaca. */
    public static String trechoHtml(String trecho) {
        if (trecho == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(trecho).replace(INICIO, "<mark>").replace(FIM, "</mark>");
    }

}
