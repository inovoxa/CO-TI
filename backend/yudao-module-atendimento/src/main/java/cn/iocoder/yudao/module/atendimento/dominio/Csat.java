package cn.iocoder.yudao.module.atendimento.dominio;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pesquisa de satisfação pelo próprio canal: depois de resolver, o contato responde com uma nota de 1 a 5.
 * Só uma resposta que é claramente uma nota conta ("5", "nota 4", "3 estrelas", "⭐⭐⭐⭐"); qualquer outra coisa é uma
 * mensagem nova e segue o fluxo normal (reabre a conversa).
 */
public final class Csat {

    private Csat() {
    }

    public static final String PERGUNTA_PADRAO = "Como você avalia este atendimento? Responda com uma nota de 1 (ruim) a 5 (ótimo).";
    public static final String AGRADECIMENTO = "Obrigado pela avaliação!";
    /** Por quantos dias depois de enviada a pesquisa aceita resposta */
    public static final int DIAS_PARA_RESPONDER = 3;

    private static final Pattern NUMERO = Pattern.compile("^(?:nota\\s*)?([1-5])\\s*(?:estrelas?|pontos?)?\\s*[.!]*$");
    private static final List<String> ESTRELAS = List.of("⭐", "★", "🌟");

    /** @return a nota, ou null se a mensagem não é uma nota */
    public static Integer nota(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.strip().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.length() > 30) {
            return null;
        }
        Matcher m = NUMERO.matcher(t);
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        // só estrelas (com ou sem espaço / seletor de variação do emoji)
        String resto = t.replace("️", "").replaceAll("\\s", "");
        int n = 0;
        while (!resto.isEmpty()) {
            String r = resto;
            String achada = ESTRELAS.stream().filter(r::startsWith).findFirst().orElse(null);
            if (achada == null) {
                return null;
            }
            resto = resto.substring(achada.length());
            n++;
        }
        return n >= 1 && n <= 5 ? n : null;
    }

    /** Mediana de uma lista ordenada (null se vazia). */
    public static Long mediana(List<Long> ordenada) {
        if (ordenada.isEmpty()) {
            return null;
        }
        int n = ordenada.size();
        return n % 2 == 1 ? ordenada.get(n / 2) : (ordenada.get(n / 2 - 1) + ordenada.get(n / 2)) / 2;
    }

}
