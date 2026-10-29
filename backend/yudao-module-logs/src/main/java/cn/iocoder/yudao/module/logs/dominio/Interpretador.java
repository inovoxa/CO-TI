package cn.iocoder.yudao.module.logs.dominio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforma o que chega pela rede em {@link Mensagem}: Syslog (RFC 3164 e 5424), chave=valor do FortiGate, GELF e
 * eventos do Windows em JSON (NXLog, Winlogbeat). Não depende de banco nem do Spring.
 */
public final class Interpretador {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** "&lt;189&gt;" no começo */
    private static final Pattern PRI = Pattern.compile("^<(\\d{1,3})>");
    /** RFC 3164: "Oct 27 11:19:03 host tag[123]: texto" */
    private static final Pattern BSD = Pattern.compile(
            "^([A-Z][a-z]{2}) +(\\d{1,2}) (\\d{2}:\\d{2}:\\d{2}) (\\S+) (.*)$", Pattern.DOTALL);
    /** rsyslog com data ISO: "2026-10-27T11:19:03-03:00 host tag: texto" */
    private static final Pattern ISO = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?) (\\S+) (.*)$", Pattern.DOTALL);
    /** tag do programa: "sshd[123]: " ou "CRON: " */
    private static final Pattern TAG = Pattern.compile("^([\\w./-]{1,64})(?:\\[(\\d+)])?: ?(.*)$", Pattern.DOTALL);
    /** chave=valor (valor entre aspas ou até o espaço) */
    private static final Pattern KV = Pattern.compile("([A-Za-z_][\\w.-]*)=(\"((?:[^\"\\\\]|\\\\.)*)\"|[^\\s]*)");
    private static final Pattern SD_ELEMENTO = Pattern.compile("\\[([^\\s\\]]+)((?:\\s+[^\\s=\\]]+=\"(?:[^\"\\\\]|\\\\.)*\")*)\\s*]");

    private static final List<String> MESES = List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
            "Oct", "Nov", "Dec");
    private static final Map<String, Integer> NIVEIS_FORTI = Map.of("emergency", 0, "alert", 1, "critical", 2,
            "error", 3, "warning", 4, "notice", 5, "information", 6, "debug", 7);

    private Interpretador() {
    }

    // ============================================================================================= Syslog

    public static Mensagem syslog(String bruto, String ip, LocalDateTime recebido, ZoneId fuso, int maximo) {
        String texto = limpar(bruto);
        int nivel = 6;
        Matcher pri = PRI.matcher(texto);
        if (pri.find()) {
            nivel = Math.min(Integer.parseInt(pri.group(1)), 191) % 8;
            texto = texto.substring(pri.end());
        }
        // FortiGate: o corpo inteiro é chave=valor, sem cabeçalho
        if (pareceForti(texto)) {
            return forti(texto, ip, recebido, fuso, maximo, nivel);
        }
        LocalDateTime momento = null;
        String origem = null;
        String aplicacao = null;
        String corpo = texto;
        Map<String, String> campos = new LinkedHashMap<>();

        if (texto.startsWith("1 ")) { // RFC 5424
            String[] p = texto.split(" ", 7);
            if (p.length >= 7) {
                momento = data(p[1], fuso);
                origem = nulo(p[2]);
                aplicacao = nulo(p[3]);
                if (nulo(p[4]) != null) {
                    campos.put("pid", p[4]);
                }
                if (nulo(p[5]) != null) {
                    campos.put("msgid", p[5]);
                }
                corpo = dadosEstruturados(p[6], campos);
            }
        } else {
            Matcher m = BSD.matcher(texto);
            if (m.matches()) {
                momento = dataBsd(m.group(1), m.group(2), m.group(3), recebido);
                origem = m.group(4);
                corpo = m.group(5);
            } else if ((m = ISO.matcher(texto)).matches()) {
                momento = data(m.group(1), fuso);
                origem = m.group(2);
                corpo = m.group(3);
            }
            Matcher t = TAG.matcher(corpo);
            if (origem != null && t.matches()) {
                aplicacao = t.group(1);
                if (t.group(2) != null) {
                    campos.put("pid", t.group(2));
                }
                corpo = t.group(3);
            }
        }
        // FortiGate atrás de um retransmissor (cabeçalho Syslog + chave=valor)
        if (pareceForti(corpo)) {
            return forti(corpo, ip, recebido, fuso, maximo, nivel);
        }
        // evento do Windows em JSON (NXLog to_json, Winlogbeat)
        String corpoLimpo = corpo.strip();
        if (corpoLimpo.startsWith("{")) {
            try {
                JsonNode no = JSON.readTree(corpoLimpo);
                if (no.isObject()) {
                    achatar("", no, campos);
                    return windowsOuMaquina(momento, recebido, origem, ip, nivel, aplicacao,
                            texto(campos, "Message", "message", "short_message", corpoLimpo), campos, fuso, maximo);
                }
            } catch (Exception ignorar) {
                // não era JSON: segue como texto
            }
        }
        return windowsOuMaquina(momento, recebido, origem, ip, nivel, aplicacao, corpo, campos, fuso, maximo);
    }

    // ============================================================================================= FortiGate

    static boolean pareceForti(String s) {
        return (s.startsWith("date=") || s.contains(" devname=")) && s.contains("logid=");
    }

    private static Mensagem forti(String texto, String ip, LocalDateTime recebido, ZoneId fuso, int maximo, int nivelPri) {
        Map<String, String> campos = chaveValor(texto);
        LocalDateTime momento = null;
        String eventtime = campos.get("eventtime");
        if (eventtime != null && eventtime.matches("\\d{10,19}")) {
            // segundos, milissegundos, microssegundos ou nanossegundos desde 1970 (depende da versão do FortiOS)
            long v = Long.parseLong(eventtime);
            int digitos = eventtime.length();
            Instant i = digitos <= 10 ? Instant.ofEpochSecond(v) : digitos <= 13 ? Instant.ofEpochMilli(v)
                    : digitos <= 16 ? Instant.ofEpochSecond(v / 1_000_000, (v % 1_000_000) * 1000)
                    : Instant.ofEpochSecond(v / 1_000_000_000, v % 1_000_000_000);
            momento = LocalDateTime.ofInstant(i, fuso);
        } else if (campos.get("date") != null && campos.get("time") != null) {
            try {
                momento = LocalDateTime.parse(campos.get("date") + "T" + campos.get("time"));
            } catch (DateTimeParseException ignorar) {
                // fica o horário de chegada
            }
        }
        Integer nivel = NIVEIS_FORTI.get(campos.getOrDefault("level", ""));
        String tipo = campos.get("type");
        return new Mensagem(ajustar(momento, recebido), recebido, Fluxos.FIREWALL,
                cortar(campos.getOrDefault("devname", ip), 255), ip, nivel == null ? nivelPri : nivel,
                tipo == null ? "fortigate" : cortar("fortigate/" + tipo, 100), cortar(texto, maximo), campos);
    }

    /** chave=valor do FortiGate (e de outros equipamentos no mesmo formato) */
    public static Map<String, String> chaveValor(String texto) {
        Map<String, String> campos = new LinkedHashMap<>();
        Matcher m = KV.matcher(texto);
        while (m.find()) {
            String valor = m.group(3) != null ? m.group(3).replace("\\\"", "\"") : m.group(2);
            campos.put(m.group(1), cortar(valor, 2000));
        }
        return campos;
    }

    // ============================================================================================= GELF

    /** GELF (Graylog Extended Log Format): JSON com host, short_message, timestamp, level e campos com "_". */
    public static Mensagem gelf(String json, String ip, LocalDateTime recebido, ZoneId fuso, int maximo) throws Exception {
        JsonNode no = JSON.readTree(json);
        if (!no.isObject()) {
            throw new IllegalArgumentException("GELF precisa ser um objeto JSON");
        }
        Map<String, String> campos = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = no.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getKey().startsWith("_") && !e.getKey().equals("_id")) {
                achatar(e.getKey().substring(1), e.getValue(), campos);
            }
        }
        LocalDateTime momento = null;
        if (no.hasNonNull("timestamp")) {
            double s = no.get("timestamp").asDouble();
            momento = LocalDateTime.ofInstant(Instant.ofEpochMilli((long) (s * 1000)), fuso);
        }
        int nivel = no.hasNonNull("level") ? Math.max(0, Math.min(7, no.get("level").asInt(6))) : 6;
        String curta = no.path("short_message").asText("");
        String completa = no.path("full_message").asText("");
        String texto = completa.isBlank() ? curta : completa;
        String origem = no.path("host").asText(null);
        String aplicacao = campos.containsKey("Channel") ? campos.get("Channel")
                : campos.containsKey("application_name") ? campos.get("application_name") : campos.get("facility");
        return windowsOuMaquina(momento, recebido, origem, ip, nivel, aplicacao, texto, campos, fuso, maximo);
    }

    // ============================================================================================= Windows

    private static Mensagem windowsOuMaquina(LocalDateTime momento, LocalDateTime recebido, String origem, String ip,
                                             int nivel, String aplicacao, String texto, Map<String, String> campos,
                                             ZoneId fuso, int maximo) {
        // Syslog de servidores e eventos do Windows que não são de contas nem de arquivos: máquinas
        String fluxo = Fluxos.MAQUINAS;
        Integer eventId = inteiro(valor(campos, "EventID", "event_id", "EventId", "winlog_event_id", "event_code",
                "winlogbeat_event_code", "winlogbeat_winlog_event_id"));
        if (eventId != null) {
            campos.put("event_id", String.valueOf(eventId));
            fluxo = EventosWindows.fluxo(eventId);
            String usuario = sufixo(campos, "TargetUserName");
            if (usuario == null || usuario.equals("-")) {
                usuario = sufixo(campos, "SubjectUserName");
            }
            putSe(campos, "usuario", usuario);
            putSe(campos, "dominio", sufixo(campos, "TargetDomainName"));
            putSe(campos, "computador", primeiro(sufixo(campos, "WorkstationName"), sufixo(campos, "Workstation"),
                    valor(campos, "Hostname", "Computer", "computer_name", "winlogbeat_winlog_computer_name")));
            putSe(campos, "ip_origem", sufixo(campos, "IpAddress"));
            putSe(campos, "objeto", sufixo(campos, "ObjectName"));
            putSe(campos, "compartilhamento", sufixo(campos, "ShareName"));
            putSe(campos, "acao", EventosWindows.nome(eventId, sufixo(campos, "AccessMask"), sufixo(campos, "AccessList")));
            if (origem == null) {
                origem = valor(campos, "Hostname", "Computer", "winlogbeat_winlog_computer_name");
            }
            // o programa do evento (Application Error, Service Control Manager...) vale mais que o nome do transporte
            String fonte = valor(campos, "SourceName", "winlogbeat_winlog_provider_name", "Channel", "winlogbeat_winlog_channel");
            if (fonte != null) {
                aplicacao = fonte;
            }
            // nível do Windows (1 crítico, 2 erro, 3 aviso, 4 informação) quando o transporte não trouxe
            Integer nivelWin = inteiro(valor(campos, "SeverityValue", "Level"));
            if (nivel == 6 && nivelWin != null && nivelWin >= 1 && nivelWin <= 3) {
                nivel = nivelWin + 1;
            }
            if (texto == null || texto.isBlank()) {
                texto = campos.getOrDefault("acao", "Evento " + eventId);
            }
        }
        if (origem == null || origem.isBlank()) {
            origem = ip == null ? "desconhecido" : ip;
        }
        return new Mensagem(ajustar(momento, recebido), recebido, fluxo, cortar(origem, 255), ip, nivel,
                cortar(aplicacao, 100), cortar(texto == null ? "" : texto, maximo), campos);
    }

    // ============================================================================================= apoio

    /** Datas no futuro (relógio do equipamento errado) ou ausentes ficam com o horário de chegada. */
    private static LocalDateTime ajustar(LocalDateTime momento, LocalDateTime recebido) {
        if (momento == null || momento.isAfter(recebido.plusMinutes(5))) {
            return recebido;
        }
        return momento;
    }

    private static LocalDateTime data(String s, ZoneId fuso) {
        if (s == null || s.equals("-")) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(OffsetDateTime.parse(s.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2"))
                    .toInstant(), fuso);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    /** RFC 3164 não tem ano: o do recebimento (ou o anterior, na virada do ano). */
    private static LocalDateTime dataBsd(String mes, String dia, String hora, LocalDateTime recebido) {
        int m = MESES.indexOf(mes) + 1;
        if (m == 0) {
            return null;
        }
        try {
            LocalDateTime d = LocalDateTime.of(LocalDate.of(recebido.getYear(), m, Integer.parseInt(dia)), LocalTime.parse(hora));
            return d.isAfter(recebido.plusDays(1)) ? d.minusYears(1) : d;
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String dadosEstruturados(String resto, Map<String, String> campos) {
        if (resto.startsWith("-")) {
            return resto.length() > 1 ? resto.substring(1).stripLeading() : "";
        }
        int fim = 0;
        Matcher m = SD_ELEMENTO.matcher(resto);
        while (m.find() && m.start() == fim) {
            campos.putAll(chaveValor(m.group(2)));
            fim = m.end();
        }
        return resto.substring(fim).stripLeading().replaceFirst("^\uFEFF", "");
    }

    private static void achatar(String prefixo, JsonNode no, Map<String, String> campos) {
        if (no.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = no.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                achatar(prefixo.isEmpty() ? e.getKey() : prefixo + "_" + e.getKey(), e.getValue(), campos);
            }
        } else if (!no.isNull() && !prefixo.isEmpty() && campos.size() < 300) {
            campos.put(cortar(prefixo, 100), cortar(no.isValueNode() ? no.asText() : no.toString(), 2000));
        }
    }

    private static String valor(Map<String, String> campos, String... chaves) {
        for (String k : chaves) {
            String v = campos.get(k);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** O campo cujo nome termina com o sufixo (Winlogbeat prefixa "winlog_event_data_"). */
    private static String sufixo(Map<String, String> campos, String nome) {
        String v = campos.get(nome);
        if (v != null) {
            return v.isBlank() ? null : v;
        }
        String fim = "_" + nome.toLowerCase();
        for (Map.Entry<String, String> e : campos.entrySet()) {
            if (e.getKey().toLowerCase().endsWith(fim) && e.getValue() != null && !e.getValue().isBlank()) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String texto(Map<String, String> campos, String a, String b, String c, String padrao) {
        String v = valor(campos, a, b, c);
        return v == null ? padrao : v;
    }

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank() && !v.equals("-")) {
                return v;
            }
        }
        return null;
    }

    private static void putSe(Map<String, String> campos, String chave, String valor) {
        if (valor != null && !valor.isBlank() && !valor.equals("-")) {
            campos.put(chave, valor);
        }
    }

    private static Integer inteiro(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nulo(String s) {
        return s == null || s.equals("-") ? null : s;
    }

    private static String limpar(String s) {
        int fim = s.length();
        while (fim > 0 && (s.charAt(fim - 1) == '\n' || s.charAt(fim - 1) == '\r' || s.charAt(fim - 1) == '\0')) {
            fim--;
        }
        return s.substring(0, fim);
    }

    static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
