package cn.iocoder.yudao.module.chat.dominio;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Regras do chat, sem banco.
 */
public final class ChatRegras {

    public static final int MAX_TEXTO = 10_000;
    public static final long MAX_ANEXO = 25L * 1024 * 1024;
    public static final int MAX_MEMBROS = 500;

    /** Só tipos do dia a dia: executável, script e página não entram, nem renomeados para outra coisa da lista. */
    private static final Set<String> EXTENSOES = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf", "txt", "csv", "doc",
            "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "zip");
    private static final Map<String, String> MIMES = Map.ofEntries(Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"), Map.entry("txt", "text/plain"), Map.entry("csv", "text/csv"));

    private ChatRegras() {
    }

    /** Uma conversa direta por par, seja quem for que a inicie. */
    public static String parChave(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    public static String extensao(String nome) {
        int i = nome == null ? -1 : nome.lastIndexOf('.');
        return i < 0 ? "" : nome.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean extensaoPermitida(String nome) {
        return EXTENSOES.contains(extensao(nome));
    }

    public static boolean imagem(String nome) {
        return Set.of("png", "jpg", "jpeg", "gif", "webp").contains(extensao(nome));
    }

    /** O tipo vem da extensão já conferida, não do que o navegador informou. */
    public static String mime(String nome) {
        return MIMES.getOrDefault(extensao(nome), "application/octet-stream");
    }

    /** Sem pastas, sem caracteres de controle e com tamanho que cabe na coluna. */
    public static String nomeSeguro(String nome) {
        String n = nome == null ? "" : nome.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (n.length() > 200) {
            String ext = extensao(n);
            n = n.substring(0, 199 - ext.length()) + "." + ext;
        }
        return n;
    }

    /** O começo do arquivo precisa combinar com a extensão, nos tipos que têm assinatura. */
    public static boolean conteudoCombina(String nome, byte[] c) {
        return switch (extensao(nome)) {
            case "png" -> comeca(c, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> comeca(c, 0xFF, 0xD8, 0xFF);
            case "gif" -> comeca(c, 'G', 'I', 'F', '8');
            case "webp" -> comeca(c, 'R', 'I', 'F', 'F') && c.length > 11 && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P';
            case "pdf" -> comeca(c, '%', 'P', 'D', 'F');
            case "zip", "docx", "xlsx", "pptx", "odt", "ods", "odp" -> comeca(c, 'P', 'K');
            case "doc", "xls", "ppt" -> comeca(c, 0xD0, 0xCF, 0x11, 0xE0);
            default -> true;
        };
    }

    private static boolean comeca(byte[] c, int... assinatura) {
        if (c == null || c.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if ((c[i] & 0xFF) != assinatura[i]) {
                return false;
            }
        }
        return true;
    }

    /** A resposta automática vale de hoje em diante dentro do período; datas vazias não limitam. */
    public static boolean ausenciaVale(Boolean ativa, java.time.LocalDate inicio, java.time.LocalDate fim, java.time.LocalDate hoje) {
        return Boolean.TRUE.equals(ativa) && (inicio == null || !hoje.isBefore(inicio)) && (fim == null || !hoje.isAfter(fim));
    }

    /** Lista gravada na coluna de menções. */
    public static String juntar(Iterable<Long> ids) {
        StringBuilder s = new StringBuilder();
        ids.forEach(i -> s.append(s.isEmpty() ? "" : ",").append(i));
        return s.isEmpty() ? null : s.toString();
    }

    public static java.util.List<Long> separar(String ids) {
        return ids == null || ids.isBlank() ? java.util.List.of() : Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
    }

}
