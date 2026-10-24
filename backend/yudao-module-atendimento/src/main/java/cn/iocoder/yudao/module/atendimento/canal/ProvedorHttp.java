package cn.iocoder.yudao.module.atendimento.canal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Chamadas HTTP às APIs dos provedores (Meta, Telegram). Sem seguir redirecionamento; o token vai no cabeçalho
 * {@code Authorization} (exceto no Telegram, em que faz parte do endereço).
 */
@Component
public class ProvedorHttp {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** Resposta do provedor: código HTTP e o JSON (vazio se não veio JSON). */
    public record Resposta(int status, String corpo, Map<String, Object> json) {

        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** Arquivo baixado do provedor. */
    public record Arquivo(byte[] dados, String tipo) {
    }

    public Resposta get(String url, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET();
        return enviar(r, bearer);
    }

    public Resposta postJson(String url, String bearer, Object corpo) throws IOException, InterruptedException {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(corpo), StandardCharsets.UTF_8));
        return enviar(r, bearer);
    }

    /** POST multipart/form-data: campos de texto e um arquivo. */
    public Resposta postArquivo(String url, String bearer, Map<String, String> campos, String campoArquivo,
                                byte[] dados, String nome, String tipo) throws IOException, InterruptedException {
        String limite = "coti" + HexFormat.of().formatHex(aleatorio(12));
        ByteArrayOutputStream b = new ByteArrayOutputStream(dados.length + 1024);
        for (Map.Entry<String, String> c : campos.entrySet()) {
            escrever(b, "--" + limite + "\r\nContent-Disposition: form-data; name=\"" + c.getKey() + "\"\r\n\r\n" + c.getValue() + "\r\n");
        }
        escrever(b, "--" + limite + "\r\nContent-Disposition: form-data; name=\"" + campoArquivo + "\"; filename=\""
                + nomeSeguro(nome) + "\"\r\nContent-Type: " + (tipo == null ? "application/octet-stream" : tipo) + "\r\n\r\n");
        b.write(dados);
        escrever(b, "\r\n--" + limite + "--\r\n");
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .POST(HttpRequest.BodyPublishers.ofByteArray(b.toByteArray()));
        return enviar(r, bearer);
    }

    /**
     * Baixa um arquivo, até {@code maximo} bytes.
     *
     * @throws Canal.FalhaDefinitiva arquivo maior que o permitido ou recusado (4xx)
     */
    public Arquivo baixar(String url, String bearer, long maximo) throws IOException, InterruptedException {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120)).GET();
        if (bearer != null) {
            r.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<InputStream> resp = http.send(r.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = resp.body()) {
            if (resp.statusCode() >= 400 && resp.statusCode() < 500 && resp.statusCode() != 429) {
                throw new Canal.FalhaDefinitiva("o provedor recusou o download da mídia (HTTP " + resp.statusCode() + ")");
            }
            if (resp.statusCode() != 200) {
                throw new IOException("download da mídia: HTTP " + resp.statusCode());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maximo) {
                    throw new Canal.FalhaDefinitiva("mídia maior que " + (maximo / 1024 / 1024) + " MB");
                }
                out.write(buf, 0, n);
            }
            return new Arquivo(out.toByteArray(), resp.headers().firstValue("Content-Type").orElse(null));
        }
    }

    private Resposta enviar(HttpRequest.Builder r, String bearer) throws IOException, InterruptedException {
        if (bearer != null) {
            r.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> resp = http.send(r.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> json;
        try {
            json = JsonUtils.parseObject(resp.body(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception semJson) {
            json = null;
        }
        return new Resposta(resp.statusCode(), resp.body(), json == null ? Map.of() : json);
    }

    private static void escrever(ByteArrayOutputStream b, String s) {
        b.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Nome de arquivo sem aspas nem quebras (vai dentro do cabeçalho do multipart). */
    static String nomeSeguro(String nome) {
        String n = nome == null || nome.isBlank() ? "arquivo" : nome.replaceAll("[\"\\\\\\r\\n]", "_");
        return n.length() > 200 ? n.substring(0, 200) : n;
    }

    private static byte[] aleatorio(int n) {
        byte[] b = new byte[n];
        ALEATORIO.nextBytes(b);
        return b;
    }

}
