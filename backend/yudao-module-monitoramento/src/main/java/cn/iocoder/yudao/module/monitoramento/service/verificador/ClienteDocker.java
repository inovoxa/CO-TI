package cn.iocoder.yudao.module.monitoramento.service.verificador;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.DockerHostDO;
import cn.iocoder.yudao.module.monitoramento.service.SegredosMonitor;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API do Docker Engine pela rede: https com certificado de cliente (o padrão do {@code dockerd --tlsverify}) ou http na
 * rede interna. Só lê: {@code GET /version} e {@code GET /containers/<nome>/json}.
 */
@Component
public class ClienteDocker {

    private static final Pattern PEM = Pattern.compile("-----BEGIN ([A-Z ]+)-----([A-Za-z0-9+/=\\s]+)-----END \\1-----");
    private static final char[] SENHA_LOCAL = "co-ti".toCharArray();

    @Resource
    private SegredosMonitor segredosMonitor;

    private record Conexao(String impressao, HttpClient cliente) {
    }

    private final Map<Long, Conexao> conexoes = new ConcurrentHashMap<>();

    /** Confere os PEM (a chave já decifrada ou ainda cifrada, como vem do cadastro). */
    public void validar(DockerHostDO h) {
        cliente(h, false);
    }

    public void esquecer(Long id) {
        if (id != null) {
            conexoes.remove(id);
        }
    }

    public String versao(DockerHostDO h) {
        JsonNode v = get(h, "/version", 15);
        if (v == null) {
            throw new IllegalStateException("a API respondeu sem a versão");
        }
        return "Docker " + v.path("Version").asText("?") + " (API " + v.path("ApiVersion").asText("?") + ")";
    }

    /** Estado do container: no ar se está rodando e, quando há healthcheck, saudável. */
    public Resultado container(DockerHostDO h, String nome, int limiteS) {
        long t0 = System.nanoTime();
        JsonNode c;
        try {
            c = get(h, "/containers/" + nome + "/json", limiteS);
        } catch (IllegalStateException e) {
            return Resultado.falha(e.getMessage());
        }
        int ms = (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
        if (c == null) {
            return Resultado.falha(ms, "container \"" + nome + "\" não encontrado em " + h.getNome());
        }
        JsonNode estado = c.path("State");
        String status = estado.path("Status").asText("desconhecido");
        if (!estado.path("Running").asBoolean(false)) {
            return Resultado.falha(ms, "container " + status + (estado.hasNonNull("ExitCode") ? " (código de saída " + estado.path("ExitCode").asInt() + ")" : ""));
        }
        JsonNode saude = estado.path("Health");
        if (saude.isMissingNode() || saude.isNull()) {
            return Resultado.ok(ms, "container rodando");
        }
        String s = saude.path("Status").asText("");
        return "healthy".equals(s) ? Resultado.ok(ms, "container rodando e saudável")
                : Resultado.falha(ms, "container rodando, mas " + ("starting".equals(s) ? "ainda iniciando" : "não saudável (" + s + ")"));
    }

    /** null = 404 (não existe). */
    private JsonNode get(DockerHostDO h, String caminho, int limiteS) {
        HttpClient cliente = cliente(h, true);
        HttpRequest req = HttpRequest.newBuilder(URI.create(h.getUrl() + caminho)).timeout(Duration.ofSeconds(Math.max(1, limiteS)))
                .header("User-Agent", "CO-TI-Monitor/1.0").GET().build();
        HttpResponse<String> resp;
        try {
            resp = cliente.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("tempo esgotado (" + limiteS + " s)");
        } catch (IOException e) {
            throw new IllegalStateException(Verificadores.erroRede(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("verificação interrompida");
        }
        if (resp.statusCode() == 404) {
            return null;
        }
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            throw new IllegalStateException("a API do Docker recusou o acesso (código " + resp.statusCode() + ")");
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("a API do Docker respondeu " + resp.statusCode());
        }
        try {
            return JsonUtils.getObjectMapper().readTree(resp.body());
        } catch (IOException e) {
            throw new IllegalStateException("resposta da API do Docker não é JSON");
        }
    }

    private HttpClient cliente(DockerHostDO h, boolean guardar) {
        String impressao = Objects.hash(h.getUrl(), h.getCa(), h.getCertificado(), h.getChave()) + "";
        Conexao c = h.getId() == null ? null : conexoes.get(h.getId());
        if (c != null && c.impressao().equals(impressao)) {
            return c.cliente();
        }
        HttpClient.Builder b = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (h.getUrl().regionMatches(true, 0, "https", 0, 5)) {
            b.sslContext(tls(h));
        }
        HttpClient novo = b.build();
        if (guardar && h.getId() != null) {
            conexoes.put(h.getId(), new Conexao(impressao, novo));
        }
        return novo;
    }

    private SSLContext tls(DockerHostDO h) {
        try {
            TrustManagerFactory tmf = null;
            if (h.getCa() != null) {
                KeyStore confiaveis = KeyStore.getInstance("PKCS12");
                confiaveis.load(null, null);
                List<Certificate> cas = certificados(h.getCa(), "CA");
                for (int i = 0; i < cas.size(); i++) {
                    confiaveis.setCertificateEntry("ca" + i, cas.get(i));
                }
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(confiaveis);
            }
            KeyManagerFactory kmf = null;
            if (h.getCertificado() != null) {
                String chave = h.getChave().startsWith("-----") ? h.getChave() : segredosMonitor.lerChaveDocker(h.getChave());
                KeyStore cliente = KeyStore.getInstance("PKCS12");
                cliente.load(null, null);
                cliente.setKeyEntry("cliente", chave(chave), SENHA_LOCAL, certificados(h.getCertificado(), "certificado do cliente").toArray(new Certificate[0]));
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(cliente, SENHA_LOCAL);
            }
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), new SecureRandom());
            return ctx;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("não foi possível montar o TLS: " + e.getMessage());
        }
    }

    static List<Certificate> certificados(String pem, String rotulo) {
        try {
            List<Certificate> l = new ArrayList<>(CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(pem.trim().getBytes(StandardCharsets.US_ASCII))));
            if (l.isEmpty()) {
                throw new IllegalArgumentException(rotulo + " sem certificado PEM");
            }
            return l;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(rotulo + " inválido: cole o conteúdo do arquivo .pem");
        }
    }

    /** PKCS#8 ("BEGIN PRIVATE KEY", RSA ou EC) ou PKCS#1 ("BEGIN RSA PRIVATE KEY"), como o openssl gera. */
    static PrivateKey chave(String pem) {
        Matcher m = PEM.matcher(pem == null ? "" : pem);
        if (!m.find()) {
            throw new IllegalArgumentException("chave do cliente inválida: cole o conteúdo do key.pem");
        }
        String tipo = m.group(1);
        byte[] der = Base64.getMimeDecoder().decode(m.group(2));
        try {
            switch (tipo) {
                case "PRIVATE KEY" -> {
                    for (String alg : List.of("RSA", "EC")) {
                        try {
                            return KeyFactory.getInstance(alg).generatePrivate(new PKCS8EncodedKeySpec(der));
                        } catch (java.security.spec.InvalidKeySpecException ignorado) {
                            // tenta o próximo algoritmo
                        }
                    }
                    throw new IllegalArgumentException("chave do cliente com algoritmo não suportado (use RSA ou EC)");
                }
                case "RSA PRIVATE KEY" -> {
                    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1ParaPkcs8(der)));
                }
                case "ENCRYPTED PRIVATE KEY" -> throw new IllegalArgumentException("a chave do cliente está protegida por senha: "
                        + "gere uma sem senha com openssl pkcs8 -topk8 -nocrypt");
                default -> throw new IllegalArgumentException("chave \"" + tipo + "\" não suportada: converta com "
                        + "openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("chave do cliente inválida: " + e.getMessage());
        }
    }

    /** Embrulha a chave RSA PKCS#1 no PrivateKeyInfo do PKCS#8 (versão 0, rsaEncryption, NULL). */
    static byte[] pkcs1ParaPkcs8(byte[] pkcs1) {
        byte[] algoritmo = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] versao = {0x02, 0x01, 0x00};
        ByteArrayOutputStream octeto = new ByteArrayOutputStream();
        octeto.write(0x04);
        comprimento(octeto, pkcs1.length);
        octeto.writeBytes(pkcs1);
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        corpo.writeBytes(versao);
        corpo.writeBytes(algoritmo);
        corpo.writeBytes(octeto.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        comprimento(out, corpo.size());
        out.writeBytes(corpo.toByteArray());
        return out.toByteArray();
    }

    private static void comprimento(ByteArrayOutputStream out, int n) {
        if (n < 0x80) {
            out.write(n);
        } else if (n <= 0xff) {
            out.write(0x81);
            out.write(n);
        } else if (n <= 0xffff) {
            out.write(0x82);
            out.write(n >> 8);
            out.write(n & 0xff);
        } else {
            out.write(0x83);
            out.write(n >> 16);
            out.write((n >> 8) & 0xff);
            out.write(n & 0xff);
        }
    }

}
