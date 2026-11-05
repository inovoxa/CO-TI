package cn.iocoder.yudao.module.infraestrutura.dominio;

import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Regras das entradas por webhook, sem banco nem AD, para poderem ser testadas sozinhas. */
public final class EntradaRegras {

    public static final long JANELA_SEGUNDOS = 300;
    /** Contas do sistema nunca são bloqueadas sozinhas, mesmo fora da lista da entrada. */
    private static final Set<String> SEMPRE_PROTEGIDOS = Set.of("admin", "administrator", "administrador", "krbtgt");

    private EntradaRegras() {
    }

    public enum Decisao {
        JA_DESATIVADA, NAO_IDENTIFICADA, VARIAS_CONTAS, PROTEGIDA, NOME_DIVERGE, CONFIRMAR_LIMITE, CONFIRMAR_MODO, EXECUTAR
    }

    /** @param bloqueios24h bloqueios já pedidos por esta entrada nas últimas 24 horas */
    public record Fatos(int contas, boolean contaAtiva, boolean protegida, boolean nomeDiverge, boolean pedeConfirmacao,
                        int bloqueios24h, int limiteDia) {
    }

    /** A ordem importa: nada que dependa de pessoa chega ao AD sem identificação única e conta liberada. */
    public static Decisao decidir(Fatos f) {
        if (f.contas() == 0) {
            return Decisao.NAO_IDENTIFICADA;
        }
        if (f.contas() > 1) {
            return Decisao.VARIAS_CONTAS;
        }
        if (!f.contaAtiva()) {
            return Decisao.JA_DESATIVADA;
        }
        if (f.protegida()) {
            return Decisao.PROTEGIDA;
        }
        if (f.nomeDiverge()) {
            return Decisao.NOME_DIVERGE;
        }
        if (f.bloqueios24h() >= f.limiteDia()) {
            return Decisao.CONFIRMAR_LIMITE;
        }
        return f.pedeConfirmacao() ? Decisao.CONFIRMAR_MODO : Decisao.EXECUTAR;
    }

    /** Lê "funcionario.matricula" de um JSON. Número e texto valem; objeto, lista, nulo ou vazio = ausente. */
    public static Optional<String> ler(JsonNode raiz, String caminho) {
        if (raiz == null || caminho == null || caminho.isBlank()) {
            return Optional.empty();
        }
        JsonNode n = raiz;
        for (String parte : caminho.strip().split("\\.")) {
            n = n.path(parte);
        }
        if (!n.isValueNode() || n.isNull() || n.isMissingNode()) {
            return Optional.empty();
        }
        String v = n.asText().strip();
        return v.isEmpty() ? Optional.empty() : Optional.of(v);
    }

    public static String assinar(byte[] segredo, String carimbo, byte[] corpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            mac.update(carimbo.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(corpo));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Confere a assinatura de carimbo.corpo; o carimbo (segundos desde 1970) fora da janela barra a repetição de um pedido antigo. */
    public static boolean assinaturaValida(byte[] segredo, String carimbo, byte[] corpo, String assinatura, Instant agora) {
        if (carimbo == null || assinatura == null) {
            return false;
        }
        long segundos;
        try {
            segundos = Long.parseLong(carimbo.strip());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(agora.getEpochSecond() - segundos) > JANELA_SEGUNDOS) {
            return false;
        }
        String recebida = assinatura.strip().toLowerCase(Locale.ROOT);
        recebida = recebida.startsWith("sha256=") ? recebida.substring(7) : recebida;
        return iguais(assinar(segredo, carimbo.strip(), corpo), recebida);
    }

    public static boolean iguais(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String hash(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @param lista IPs ou redes CIDR separados por vírgula; vazia = qualquer origem */
    public static boolean ipPermitido(String ip, String lista) {
        if (lista == null || lista.isBlank()) {
            return true;
        }
        for (String item : lista.split(",")) {
            if (!item.isBlank() && dentro(ip, item.strip())) {
                return true;
            }
        }
        return false;
    }

    private static boolean dentro(String ip, String rede) {
        try {
            int barra = rede.indexOf('/');
            // getByName não consulta DNS para endereços literais; nomes são recusados antes
            if (!literal(ip) || !literal(barra < 0 ? rede : rede.substring(0, barra))) {
                return false;
            }
            byte[] a = InetAddress.getByName(ip).getAddress();
            byte[] b = InetAddress.getByName(barra < 0 ? rede : rede.substring(0, barra)).getAddress();
            if (a.length != b.length) {
                return false;
            }
            int bits = barra < 0 ? a.length * 8 : Integer.parseInt(rede.substring(barra + 1));
            if (bits < 0 || bits > a.length * 8) {
                return false;
            }
            for (int i = 0; i < bits; i++) {
                int mascara = 0x80 >> (i % 8);
                if ((a[i / 8] & mascara) != (b[i / 8] & mascara)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean literal(String s) {
        return s != null && s.matches("[0-9a-fA-F:.]+") && (s.contains(":") || s.matches("[0-9.]+"));
    }

    public static boolean contem(String listaCsv, String valor) {
        if (listaCsv == null || valor == null) {
            return false;
        }
        return Arrays.stream(listaCsv.split(",")).anyMatch(v -> v.strip().equalsIgnoreCase(valor.strip()));
    }

    public static boolean loginProtegido(String login, String lista) {
        return login != null && (SEMPRE_PROTEGIDOS.contains(login.toLowerCase(Locale.ROOT)) || contem(lista, login));
    }

    /** Compara nomes sem acento, caixa ou espaços a mais. */
    public static boolean mesmoNome(String a, String b) {
        return normalizar(a).equals(normalizar(b));
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Aceita 2026-11-05, 05/11/2026 e 2026-11-05T14:30:00. Data sem hora vale desde o início do dia. */
    public static Optional<LocalDateTime> data(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String t = texto.strip();
        try {
            if (t.length() > 10) {
                return Optional.of(LocalDateTime.parse(t.length() > 19 ? t.substring(0, 19) : t));
            }
            return Optional.of((t.contains("/") ? LocalDate.parse(t, DateTimeFormatter.ofPattern("dd/MM/uuuu")) : LocalDate.parse(t)).atStartOfDay());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

}
