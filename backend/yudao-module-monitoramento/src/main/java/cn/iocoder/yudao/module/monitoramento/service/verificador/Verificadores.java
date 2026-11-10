package cn.iocoder.yudao.module.monitoramento.service.verificador;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dominio.Regras;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * As verificações de cada tipo de monitor. Cada uma devolve um {@link Resultado}; quem decide o estado (pendente,
 * fora, aviso) é a máquina de estados.
 */
@Component
public class Verificadores {

    /** Corpo lido no máximo (palavra-chave e JSON) */
    static final int MAX_CORPO = 1024 * 1024;
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([A-Za-z0-9._\\-]+)");

    static final HttpClient HTTP = cliente(false);
    static final HttpClient HTTP_SEM_TLS = cliente(true);
    private final HttpClient http = HTTP;
    private final HttpClient httpSemTls = HTTP_SEM_TLS;

    @jakarta.annotation.Resource
    private VerificadoresServicos servicos;

    public Resultado verificar(MonitorDO m) {
        return switch (m.getTipo()) {
            case "http", "palavra", "json" -> http(m);
            case "tcp" -> tcp(m);
            case "ping" -> ping(m);
            case "dns" -> dns(m);
            default -> cn.iocoder.yudao.module.monitoramento.dominio.TiposMonitor.SERVICOS.contains(m.getTipo()) ? servicos.verificar(m)
                    : Resultado.falha("tipo sem verificação ativa: " + m.getTipo());
        };
    }

    /** "Ignorar erros de TLS" nos tipos que abrem o socket direto (Redis, SMTP, MQTT, MongoDB). */
    static SSLContext contextoSemVerificacao() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new AceitaTudo()}, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static SSLSocketFactory fabricaSemVerificacao() {
        return contextoSemVerificacao().getSocketFactory();
    }

    // ================================================================================== HTTP

    Resultado http(MonitorDO m) {
        URI uri;
        try {
            uri = URI.create(m.getAlvo().trim());
            if (uri.getScheme() == null || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT)) || uri.getHost() == null) {
                return Resultado.falha("endereço inválido: use http:// ou https://");
            }
        } catch (Exception e) {
            return Resultado.falha("endereço inválido: " + e.getMessage());
        }
        Duration limite = Duration.ofSeconds(m.getTempoLimiteS());
        String metodo = Optional.ofNullable(m.getMetodo()).orElse("GET").toUpperCase(Locale.ROOT);
        Map<String, String> cabecalhos = cabecalhos(m.getCabecalhos());
        HttpClient cliente = Boolean.TRUE.equals(m.getIgnorarTls()) ? httpSemTls : http;
        long t0 = System.nanoTime();
        int redirecionamentos = 0;
        HttpResponse<InputStream> resp;
        try {
            while (true) {
                HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(limite).header("User-Agent", "CO-TI-Monitor/1.0");
                for (Map.Entry<String, String> h : cabecalhos.entrySet()) {
                    b.setHeader(h.getKey(), h.getValue());
                }
                String corpo = m.getCorpo();
                b.method(metodo, corpo == null || corpo.isEmpty() || metodo.equals("GET") || metodo.equals("HEAD")
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo));
                resp = cliente.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
                int c = resp.statusCode();
                Optional<String> destino = resp.headers().firstValue("location");
                if (c >= 300 && c < 400 && c != 304 && destino.isPresent() && redirecionamentos < Optional.ofNullable(m.getMaxRedirecionamentos()).orElse(10)) {
                    resp.body().close();
                    uri = uri.resolve(destino.get());
                    redirecionamentos++;
                    if (c == 303 || ((c == 301 || c == 302) && metodo.equals("POST"))) {
                        metodo = "GET";
                    }
                    continue;
                }
                break;
            }
        } catch (IllegalArgumentException e) {
            return Resultado.falha("pedido inválido: " + e.getMessage());
        } catch (HttpConnectTimeoutException e) {
            return Resultado.falha("tempo esgotado ao conectar (" + m.getTempoLimiteS() + " s)");
        } catch (HttpTimeoutException e) {
            return Resultado.falha("tempo esgotado (" + m.getTempoLimiteS() + " s)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.falha("verificação interrompida");
        } catch (IOException e) {
            return Resultado.falha(erroRede(e));
        }
        String texto;
        try (InputStream in = resp.body()) {
            texto = new String(in.readNBytes(MAX_CORPO), charset(resp));
        } catch (IOException e) {
            return Resultado.falha("falha ao ler a resposta: " + erroRede(e));
        }
        int ms = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        Resultado r = avaliarResposta(m, resp.statusCode(), texto, ms);
        return certificado(resp, r);
    }

    /** Código, palavra-chave e consulta JSON, como o Kuma faz depois da resposta. */
    static Resultado avaliarResposta(MonitorDO m, int codigo, String texto, int ms) {
        if (!Regras.codigoAceito(m.getCodigosAceitos(), codigo)) {
            return Resultado.falha(ms, "código " + codigo + " (esperado " + Optional.ofNullable(m.getCodigosAceitos()).orElse("200-299") + ")");
        }
        if ("palavra".equals(m.getTipo())) {
            boolean achou = texto.contains(Optional.ofNullable(m.getPalavra()).orElse(""));
            boolean invertida = Boolean.TRUE.equals(m.getPalavraInvertida());
            if (achou == !invertida) {
                return Resultado.ok(ms, "código " + codigo + ", palavra " + (achou ? "encontrada" : "ausente, como esperado"));
            }
            String trecho = texto.replaceAll("\\s+", " ").strip();
            return Resultado.falha(ms, "código " + codigo + ", mas a palavra " + (achou ? "está presente" : "não foi encontrada")
                    + " [" + (trecho.length() > 60 ? trecho.substring(0, 60) + "…" : trecho) + "]");
        }
        if ("json".equals(m.getTipo())) {
            Regras.ResultadoJson j;
            try {
                j = Regras.consultaJson(texto, m.getJsonCaminho(), m.getJsonOperador(), m.getJsonEsperado());
            } catch (IllegalArgumentException e) {
                return Resultado.falha(ms, e.getMessage());
            }
            if (j.erro() != null) {
                return Resultado.falha(ms, j.erro());
            }
            String comparacao = "(" + j.valor() + " " + Optional.ofNullable(m.getJsonOperador()).orElse("==") + " " + m.getJsonEsperado() + ")";
            return j.ok() ? Resultado.ok(ms, "consulta JSON confere " + comparacao) : Resultado.falha(ms, "consulta JSON não confere " + comparacao);
        }
        return Resultado.ok(ms, "código " + codigo);
    }

    private static Resultado certificado(HttpResponse<?> resp, Resultado r) {
        Optional<SSLSession> s = resp.sslSession();
        if (s.isEmpty()) {
            return r;
        }
        try {
            Certificate[] cadeia = s.get().getPeerCertificates();
            if (cadeia.length > 0 && cadeia[0] instanceof X509Certificate x) {
                LocalDateTime validade = LocalDateTime.ofInstant(x.getNotAfter().toInstant(), ZoneId.systemDefault());
                long dias = Duration.between(LocalDateTime.now(), validade).toDays();
                return r.comCertificado((int) dias, validade);
            }
        } catch (SSLPeerUnverifiedException ignorado) {
            // sem certificado do servidor
        }
        return r;
    }

    private static Map<String, String> cabecalhos(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> m = JsonUtils.parseObject(json, new TypeReference<>() {
            });
            Map<String, String> out = new LinkedHashMap<>();
            if (m != null) {
                m.forEach((k, v) -> out.put(k, v == null ? "" : String.valueOf(v)));
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Charset charset(HttpResponse<?> resp) {
        Matcher mt = CHARSET.matcher(resp.headers().firstValue("content-type").orElse(""));
        if (mt.find()) {
            try {
                return Charset.forName(mt.group(1));
            } catch (Exception ignorado) {
                // charset desconhecido: UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static HttpClient cliente(boolean semTls) {
        HttpClient.Builder b = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(30));
        if (semTls) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{new AceitaTudo()}, new SecureRandom());
                b.sslContext(ctx);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return b.build();
    }

    /** "Ignorar erros de TLS": aceita certificado vencido, autoassinado ou de outro nome (como o Kuma). */
    private static final class AceitaTudo extends X509ExtendedTrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] c, String a, Socket s) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a, Socket s) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] c, String a) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    // ================================================================================== TCP

    Resultado tcp(MonitorDO m) {
        if (!Regras.hostValido(m.getAlvo()) || m.getPorta() == null || m.getPorta() < 1 || m.getPorta() > 65535) {
            return Resultado.falha("informe o host e a porta (1 a 65535)");
        }
        long t0 = System.nanoTime();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(m.getAlvo(), m.getPorta()), m.getTempoLimiteS() * 1000);
            int ms = (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
            return Resultado.ok(ms, "porta " + m.getPorta() + " aberta");
        } catch (SocketTimeoutException e) {
            return Resultado.falha("tempo esgotado (" + m.getTempoLimiteS() + " s)");
        } catch (IOException e) {
            return Resultado.falha(erroRede(e));
        }
    }

    // ================================================================================== ping

    Resultado ping(MonitorDO m) {
        String host = m.getAlvo() == null ? null : m.getAlvo().trim();
        if (!Regras.hostValido(host)) {
            return Resultado.falha("host inválido");
        }
        int limite = Math.max(1, m.getTempoLimiteS());
        // argumentos separados (sem shell); o host já foi validado e não começa com "-"
        ProcessBuilder pb = new ProcessBuilder("ping", "-n", "-c", "1", "-W", String.valueOf(limite), host).redirectErrorStream(true);
        try {
            Process p = pb.start();
            byte[] saida;
            try (InputStream in = p.getInputStream()) {
                if (!p.waitFor(limite + 3L, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    return Resultado.falha("tempo esgotado (" + limite + " s)");
                }
                saida = in.readNBytes(8192);
            }
            String texto = new String(saida, StandardCharsets.UTF_8);
            Integer ms = Regras.tempoPing(texto);
            if (p.exitValue() == 0 && ms != null) {
                return Resultado.ok(ms, "respondeu ao ping");
            }
            if (texto.contains("unknown host") || texto.contains("Name or service not known") || texto.contains("bad address")) {
                return Resultado.falha("nome não encontrado (DNS)");
            }
            return Resultado.falha("sem resposta ao ping em " + limite + " s");
        } catch (IOException e) {
            return Resultado.falha("ping indisponível no servidor: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.falha("verificação interrompida");
        }
    }

    // ================================================================================== DNS

    Resultado dns(MonitorDO m) {
        String host = m.getAlvo() == null ? null : m.getAlvo().trim();
        if (!Regras.hostValido(host)) {
            return Resultado.falha("nome inválido");
        }
        String tipo = Optional.ofNullable(m.getDnsTipo()).orElse("A").toUpperCase(Locale.ROOT);
        if (!Set.of("A", "AAAA", "CNAME", "MX", "NS", "TXT", "SOA", "SRV", "PTR", "CAA").contains(tipo)) {
            return Resultado.falha("tipo de registro inválido: " + tipo);
        }
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        String servidor = m.getDnsServidor() == null || m.getDnsServidor().isBlank() ? null : m.getDnsServidor().trim();
        if (servidor != null && !Regras.hostValido(servidor)) {
            return Resultado.falha("servidor DNS inválido");
        }
        env.put(Context.PROVIDER_URL, servidor == null ? "dns:" : "dns://" + servidor);
        env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(Math.max(1, m.getTempoLimiteS()) * 1000));
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        long t0 = System.nanoTime();
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(host, new String[]{tipo});
            Attribute a = attrs.get(tipo);
            int ms = (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
            if (a == null || a.size() == 0) {
                return Resultado.falha(ms, "sem registro " + tipo + " para " + host);
            }
            List<String> valores = new ArrayList<>();
            for (int i = 0; i < a.size(); i++) {
                valores.add(String.valueOf(a.get(i)));
            }
            String lista = String.join(", ", valores);
            String esperado = m.getDnsEsperado();
            if (esperado != null && !esperado.isBlank() && valores.stream().noneMatch(v -> v.contains(esperado.trim()))) {
                return Resultado.falha(ms, tipo + " " + lista + " não contém " + esperado.trim());
            }
            return Resultado.ok(ms, tipo + " " + (lista.length() > 300 ? lista.substring(0, 300) + "…" : lista));
        } catch (NameNotFoundException e) {
            return Resultado.falha("nome não existe (NXDOMAIN)");
        } catch (javax.naming.CommunicationException e) {
            return Resultado.falha("servidor DNS não respondeu");
        } catch (javax.naming.NamingException e) {
            return Resultado.falha("falha na consulta DNS: " + Optional.ofNullable(e.getExplanation()).orElse(e.getClass().getSimpleName()));
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (javax.naming.NamingException ignorado) {
                    // nada
                }
            }
        }
    }

    // ================================================================================== mensagens

    static String erroRede(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        if (c instanceof UnknownHostException) {
            return "nome não encontrado (DNS): " + c.getMessage();
        }
        if (c instanceof ConnectException || String.valueOf(c.getMessage()).contains("Connection refused")) {
            return "conexão recusada";
        }
        if (c instanceof NoRouteToHostException) {
            return "sem rota até o host";
        }
        if (c instanceof SocketTimeoutException) {
            return "tempo esgotado";
        }
        if (c instanceof java.security.cert.CertificateException || c instanceof java.security.GeneralSecurityException
                || hasCause(e, javax.net.ssl.SSLHandshakeException.class) && String.valueOf(c.getMessage()).contains("certification path")) {
            return "certificado TLS inválido: " + c.getMessage();
        }
        if (c instanceof SSLException) {
            return "falha de TLS: " + c.getMessage();
        }
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> tipo) {
        for (Throwable c = e; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (tipo.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

}
