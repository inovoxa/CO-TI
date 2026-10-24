package cn.iocoder.yudao.module.atendimento.dominio;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Telefone no formato E.164 ({@code +5516999990000}), como o WhatsApp e o Telegram usam.
 *
 * <p>Particularidade do Brasil: o WhatsApp devolve alguns celulares antigos sem o nono dígito
 * ({@code 551699990000} em vez de {@code 5516999990000}). Para achar o mesmo contato, as duas formas são
 * consideradas iguais ({@link #variantes}).
 */
public final class Telefone {

    private Telefone() {
    }

    /**
     * Normaliza o que veio do canal ou da tela. Sem código do país e com 10 ou 11 dígitos, assume Brasil.
     *
     * @return {@code +} e só dígitos, ou null se não parece telefone
     */
    public static String normalizar(String bruto) {
        if (bruto == null) {
            return null;
        }
        String s = bruto.trim();
        // WhatsApp/Evolution: "5516999990000@s.whatsapp.net"
        int arroba = s.indexOf('@');
        if (arroba > 0) {
            s = s.substring(0, arroba);
        }
        boolean internacional = s.startsWith("+") || s.startsWith("00");
        String d = s.replaceAll("\\D", "");
        if (s.startsWith("00")) {
            d = d.substring(2);
        }
        if (!internacional && (d.length() == 10 || d.length() == 11)) {
            d = "55" + d;
        }
        if (d.length() < 8 || d.length() > 15) {
            return null;
        }
        return "+" + d;
    }

    /** O número e, se for celular brasileiro, a outra forma (com e sem o nono dígito). */
    public static Set<String> variantes(String e164) {
        Set<String> out = new LinkedHashSet<>();
        if (e164 == null) {
            return out;
        }
        out.add(e164);
        if (e164.startsWith("+55")) {
            String resto = e164.substring(3);   // DDD + número
            if (resto.length() == 11 && resto.charAt(2) == '9') {
                out.add("+55" + resto.substring(0, 2) + resto.substring(3));          // sem o 9
            } else if (resto.length() == 10 && "6789".indexOf(resto.charAt(2)) >= 0) {
                out.add("+55" + resto.substring(0, 2) + "9" + resto.substring(2));    // celular sem o 9: com
            }
        }
        return out;
    }

}
