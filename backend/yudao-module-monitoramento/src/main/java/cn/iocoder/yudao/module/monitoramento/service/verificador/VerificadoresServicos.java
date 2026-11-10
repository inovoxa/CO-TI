package cn.iocoder.yudao.module.monitoramento.service.verificador;

import cn.iocoder.yudao.module.inventario.service.SnmpCliente;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.DockerHostDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonDockerHostMapper;
import cn.iocoder.yudao.module.monitoramento.dominio.Regras;
import cn.iocoder.yudao.module.monitoramento.dominio.RegrasServicos;
import cn.iocoder.yudao.module.monitoramento.dominio.TiposMonitor;
import cn.iocoder.yudao.module.monitoramento.service.MonitorService;
import cn.iocoder.yudao.module.monitoramento.service.SegredosMonitor;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import jakarta.annotation.Resource;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Verificações dos tipos de serviço. A senha é decifrada só aqui, na hora, e nunca entra na mensagem gravada.
 */
@Component
public class VerificadoresServicos {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Resource
    private SegredosMonitor segredosMonitor;
    @Resource
    private MonDockerHostMapper monDockerHostMapper;
    @Resource
    private ClienteDocker clienteDocker;

    public Resultado verificar(MonitorDO m) {
        Map<String, String> s;
        try {
            s = segredosMonitor.ler(m.getSegredos());
        } catch (RuntimeException e) {
            return Resultado.falha("senha guardada ilegível: confira a CO_TI_CHAVE_MESTRA ou digite a senha de novo");
        }
        Map<String, Object> o = MonitorService.opcoes(m.getOpcoes());
        String senha = s.get(SegredosMonitor.SENHA), segredo = s.get(SegredosMonitor.SEGREDO);
        boolean tls = Boolean.TRUE.equals(o.get(TiposMonitor.TLS));
        return switch (m.getTipo()) {
            case "postgres", "mysql", "sqlserver", "oracle" -> sql(m, senha, tls);
            case "snmp" -> snmp(m, o, senha, segredo);
            case "docker" -> docker(m);
            case "redis" -> redis(m, senha, tls);
            case "mongodb" -> mongodb(m, senha, tls);
            case "smtp" -> smtp(m, String.valueOf(o.getOrDefault(TiposMonitor.SMTP_SEGURANCA, "nenhuma")));
            case "radius" -> radius(m, senha, segredo);
            case "mqtt" -> mqtt(m, senha, tls);
            case "rabbitmq" -> rabbitmq(m, senha);
            default -> Resultado.falha("tipo sem verificação ativa: " + m.getTipo());
        };
    }

    private static int ms(long t0) {
        return (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
    }

    /** Valor lido confere com a condição? Sem valor esperado, basta ter respondido. */
    static Resultado comparar(int ms, String rotulo, String valor, MonitorDO m) {
        String esperado = m.getJsonEsperado();
        if (esperado == null || esperado.isBlank()) {
            return Resultado.ok(ms, rotulo + (valor == null ? "" : ": " + cortar(valor)));
        }
        String op = Optional.ofNullable(m.getJsonOperador()).orElse("==");
        String json = "{\"v\":" + (valor == null ? "null" : cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(valor)) + "}";
        Regras.ResultadoJson r = Regras.consultaJson(json, "v", op, esperado);
        if (r.erro() != null) {
            return Resultado.falha(ms, r.erro());
        }
        String comparacao = "(" + cortar(valor) + " " + op + " " + esperado + ")";
        return r.ok() ? Resultado.ok(ms, rotulo + " confere " + comparacao) : Resultado.falha(ms, rotulo + " não confere " + comparacao);
    }

    private static String cortar(String s) {
        return s == null ? "null" : s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    // ================================================================================== SQL

    Resultado sql(MonitorDO m, String senha, boolean tls) {
        int limite = Math.max(1, m.getTempoLimiteS());
        String url = RegrasServicos.urlJdbc(m.getTipo(), m.getAlvo(), m.getPorta(), m.getBanco(), tls, Boolean.TRUE.equals(m.getIgnorarTls()), limite);
        Properties p = new Properties();
        p.setProperty("user", m.getUsuario());
        if (senha != null) {
            p.setProperty("password", senha);
        }
        if ("oracle".equals(m.getTipo())) {
            p.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(limite * 1000));
            p.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(limite * 1000));
        }
        long t0 = System.nanoTime();
        try (Connection c = DriverManager.getConnection(url, p); Statement st = c.createStatement()) {
            st.setQueryTimeout(limite);
            st.setMaxRows(1);
            String valor = null;
            if (st.execute(m.getConsulta())) {
                try (ResultSet rs = st.getResultSet()) {
                    if (rs.next()) {
                        valor = rs.getString(1);
                    }
                }
            }
            return comparar(ms(t0), "consulta respondeu", valor, m);
        } catch (SQLException e) {
            return Resultado.falha(ms(t0), erroSql(e));
        } catch (RuntimeException e) {
            return Resultado.falha(ms(t0), Verificadores.erroRede(e));
        }
    }

    static String erroSql(SQLException e) {
        String estado = Objects.toString(e.getSQLState(), "");
        String msg = Objects.toString(e.getMessage(), e.getClass().getSimpleName()).replaceAll("\\s+", " ");
        if (estado.startsWith("28") || msg.contains("password authentication failed") || msg.contains("Access denied") || msg.contains("Login failed")
                || msg.contains("ORA-01017")) {
            return "usuário ou senha recusados pelo banco";
        }
        if (estado.startsWith("08") || e.getCause() instanceof IOException) {
            return "sem conexão com o banco: " + (e.getCause() != null ? Verificadores.erroRede(e.getCause()) : msg);
        }
        if (msg.contains("No suitable driver")) {
            return "driver do banco não está instalado no CO-TI";
        }
        return msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
    }

    // ================================================================================== SNMP

    Resultado snmp(MonitorDO m, Map<String, Object> o, String senha, String segredo) {
        String versao = String.valueOf(o.getOrDefault(TiposMonitor.SNMP_VERSAO, "v2c"));
        SnmpCliente.Credencial c = new SnmpCliente.Credencial(versao, senha, m.getUsuario(), (String) o.get(TiposMonitor.SNMP_NIVEL),
                (String) o.get(TiposMonitor.SNMP_AUTH), senha, (String) o.get(TiposMonitor.SNMP_PRIV), segredo, m.getPorta());
        long t0 = System.nanoTime();
        String endereco;
        try {
            endereco = InetAddress.getByName(m.getAlvo()).getHostAddress();
        } catch (UnknownHostException e) {
            return Resultado.falha("nome não encontrado (DNS): " + m.getAlvo());
        }
        try (SnmpCliente cliente = new SnmpCliente(Math.max(1, m.getTempoLimiteS()) * 1000, 0)) {
            SnmpCliente.Valor v = cliente.valor(endereco, c, m.getConsulta().replaceFirst("^\\.", ""));
            if (v == null) {
                return Resultado.falha(ms(t0), "sem resposta SNMP (confira " + ("v3".equals(versao) ? "usuário e senhas" : "a comunidade") + " e a versão)");
            }
            if (v.erro() != null) {
                return Resultado.falha(ms(t0), v.erro());
            }
            return comparar(ms(t0), "OID " + m.getConsulta(), v.texto(), m);
        } catch (IOException e) {
            return Resultado.falha(ms(t0), Verificadores.erroRede(e));
        }
    }

    // ================================================================================== Docker

    Resultado docker(MonitorDO m) {
        DockerHostDO h = m.getDockerHostId() == null ? null : monDockerHostMapper.selectById(m.getDockerHostId());
        if (h == null) {
            return Resultado.falha("servidor Docker não encontrado: escolha outro no monitor");
        }
        try {
            return clienteDocker.container(h, m.getConsulta(), Math.max(1, m.getTempoLimiteS()));
        } catch (IllegalArgumentException e) {
            return Resultado.falha(e.getMessage());
        }
    }

    // ================================================================================== sockets

    private Socket conectar(MonitorDO m, boolean tls) throws IOException {
        int limite = Math.max(1, m.getTempoLimiteS()) * 1000;
        Socket s = new Socket();
        s.connect(new InetSocketAddress(m.getAlvo(), m.getPorta()), limite);
        s.setSoTimeout(limite);
        return tls ? tls(s, m) : s;
    }

    private static SSLSocket tls(Socket s, MonitorDO m) throws IOException {
        SSLSocketFactory f = Boolean.TRUE.equals(m.getIgnorarTls()) ? Verificadores.fabricaSemVerificacao() : (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) f.createSocket(s, m.getAlvo(), m.getPorta(), true);
        if (!Boolean.TRUE.equals(m.getIgnorarTls())) {
            javax.net.ssl.SSLParameters p = ssl.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(p);
        }
        ssl.startHandshake();
        return ssl;
    }

    private static Resultado comCertificado(Resultado r, Socket s) {
        if (s instanceof SSLSocket ssl) {
            try {
                Certificate[] cadeia = ssl.getSession().getPeerCertificates();
                if (cadeia.length > 0 && cadeia[0] instanceof X509Certificate x) {
                    LocalDateTime validade = LocalDateTime.ofInstant(x.getNotAfter().toInstant(), ZoneId.systemDefault());
                    return r.comCertificado((int) Duration.between(LocalDateTime.now(), validade).toDays(), validade);
                }
            } catch (javax.net.ssl.SSLPeerUnverifiedException ignorado) {
                // sem certificado
            }
        }
        return r;
    }

    Resultado redis(MonitorDO m, String senha, boolean tls) {
        long t0 = System.nanoTime();
        try (Socket s = conectar(m, tls)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            if (senha != null) {
                out.write(m.getUsuario() == null ? Protocolos.comandoRedis("AUTH", senha) : Protocolos.comandoRedis("AUTH", m.getUsuario(), senha));
                out.flush();
                String r = Protocolos.linha(in);
                if (!r.startsWith("+OK")) {
                    return Resultado.falha(ms(t0), r.contains("WRONGPASS") || r.contains("invalid") ? "usuário ou senha recusados pelo Redis"
                            : "AUTH recusado: " + cortar(r.replaceFirst("^-", "")));
                }
            }
            out.write(Protocolos.comandoRedis("PING"));
            out.flush();
            String r = Protocolos.linha(in);
            out.write(Protocolos.comandoRedis("QUIT"));
            out.flush();
            if (r.startsWith("+PONG")) {
                return comCertificado(Resultado.ok(ms(t0), "Redis respondeu PONG"), s);
            }
            return Resultado.falha(ms(t0), r.contains("NOAUTH") ? "o Redis exige senha" : "resposta inesperada: " + cortar(r));
        } catch (IOException e) {
            return Resultado.falha(ms(t0), Verificadores.erroRede(e));
        }
    }

    Resultado smtp(MonitorDO m, String seguranca) {
        long t0 = System.nanoTime();
        try (Socket bruto = conectar(m, "tls".equals(seguranca))) {
            Socket s = bruto;
            String banner = respostaSmtp(s.getInputStream());
            if (!banner.startsWith("220")) {
                return Resultado.falha(ms(t0), "o servidor não aceitou a conexão: " + cortar(banner));
            }
            comandoSmtp(s, "EHLO co-ti.monitor");
            String ehlo = respostaSmtp(s.getInputStream());
            if (!ehlo.startsWith("250")) {
                return Resultado.falha(ms(t0), "EHLO recusado: " + cortar(ehlo));
            }
            if ("starttls".equals(seguranca)) {
                comandoSmtp(s, "STARTTLS");
                String r = respostaSmtp(s.getInputStream());
                if (!r.startsWith("220")) {
                    return Resultado.falha(ms(t0), "o servidor não ofereceu STARTTLS: " + cortar(r));
                }
                s = tls(s, m);
                comandoSmtp(s, "EHLO co-ti.monitor");
                if (!respostaSmtp(s.getInputStream()).startsWith("250")) {
                    return Resultado.falha(ms(t0), "EHLO recusado depois do STARTTLS");
                }
            }
            comandoSmtp(s, "QUIT");
            Resultado ok = Resultado.ok(ms(t0), "SMTP respondeu " + cortar(banner.split("\n")[0]));
            return comCertificado(ok, s);
        } catch (IOException e) {
            return Resultado.falha(ms(t0), Verificadores.erroRede(e));
        }
    }

    private static void comandoSmtp(Socket s, String c) throws IOException {
        s.getOutputStream().write((c + "\r\n").getBytes(StandardCharsets.US_ASCII));
        s.getOutputStream().flush();
    }

    /** Lê uma resposta SMTP, inclusive as de várias linhas (250-...). */
    static String respostaSmtp(InputStream in) throws IOException {
        StringBuilder tudo = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            String l = Protocolos.linha(in);
            tudo.append(l).append('\n');
            if (l.length() < 4 || l.charAt(3) != '-') {
                break;
            }
        }
        return tudo.toString().trim();
    }

    Resultado radius(MonitorDO m, String senha, String segredo) {
        int id = ALEATORIO.nextInt(256);
        byte[] autenticador = new byte[16];
        ALEATORIO.nextBytes(autenticador);
        byte[] pedido = Protocolos.radiusPedido(id, autenticador, m.getUsuario(), senha, segredo);
        long t0 = System.nanoTime();
        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(Math.max(1, m.getTempoLimiteS()) * 1000);
            InetAddress endereco = InetAddress.getByName(m.getAlvo());
            s.send(new DatagramPacket(pedido, pedido.length, endereco, m.getPorta()));
            byte[] buf = new byte[4096];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            while (true) {
                s.receive(resp);
                if (resp.getLength() >= 20 && (buf[1] & 0xff) == id) {
                    break;
                }
            }
            int n = resp.getLength();
            if (!Protocolos.respostaAutentica(buf, n, autenticador, segredo)) {
                return Resultado.falha(ms(t0), "resposta com outro segredo compartilhado: confira o segredo do RADIUS");
            }
            String msg = Protocolos.mensagem(buf, n);
            return switch (buf[0] & 0xff) {
                case Protocolos.ACCESS_ACCEPT -> Resultado.ok(ms(t0), "RADIUS aceitou o acesso" + (msg == null ? "" : ": " + cortar(msg)));
                case Protocolos.ACCESS_REJECT -> Resultado.falha(ms(t0), "RADIUS recusou o acesso" + (msg == null ? " (usuário ou senha)" : ": " + cortar(msg)));
                case Protocolos.ACCESS_CHALLENGE -> Resultado.falha(ms(t0), "RADIUS pediu um segundo fator (Access-Challenge)");
                default -> Resultado.falha(ms(t0), "resposta RADIUS inesperada (código " + (buf[0] & 0xff) + ")");
            };
        } catch (SocketTimeoutException e) {
            return Resultado.falha("sem resposta do RADIUS em " + m.getTempoLimiteS() + " s (confira o IP do CO-TI como cliente no servidor)");
        } catch (IOException e) {
            return Resultado.falha(ms(t0), Verificadores.erroRede(e));
        }
    }

    Resultado mqtt(MonitorDO m, String senha, boolean tls) {
        long t0 = System.nanoTime();
        try (Socket s = conectar(m, tls)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            out.write(Protocolos.mqttConnect("co-ti-" + Long.toHexString(ALEATORIO.nextLong()), m.getUsuario(), senha, 30));
            out.flush();
            byte[] connack = Protocolos.lerPacoteMqtt(in);
            if ((connack[0] & 0xf0) != 0x20 || connack.length < 3) {
                return Resultado.falha(ms(t0), "o servidor não respondeu como MQTT");
            }
            int codigo = connack[2] & 0xff;
            if (codigo != 0) {
                return Resultado.falha(ms(t0), switch (codigo) {
                    case 4, 5 -> "usuário ou senha recusados pelo MQTT";
                    case 1 -> "versão do MQTT recusada (o CO-TI usa 3.1.1)";
                    default -> "conexão MQTT recusada (código " + codigo + ")";
                });
            }
            String topico = m.getConsulta();
            if (topico == null || topico.isBlank()) {
                out.write(Protocolos.mqttDisconnect());
                return comCertificado(Resultado.ok(ms(t0), "MQTT aceitou a conexão"), s);
            }
            out.write(Protocolos.mqttSubscribe(1, topico));
            out.flush();
            long fim = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, m.getTempoLimiteS()));
            while (System.nanoTime() < fim) {
                byte[] p;
                try {
                    p = Protocolos.lerPacoteMqtt(in);
                } catch (SocketTimeoutException e) {
                    break;
                }
                if ((p[0] & 0xf0) == 0x30) {
                    String conteudo = Protocolos.conteudoPublish(p);
                    out.write(Protocolos.mqttDisconnect());
                    String palavra = m.getPalavra();
                    if (palavra != null && !palavra.isBlank() && !conteudo.contains(palavra)) {
                        return Resultado.falha(ms(t0), "mensagem em " + topico + " sem \"" + palavra + "\": " + cortar(conteudo));
                    }
                    return comCertificado(Resultado.ok(ms(t0), "mensagem em " + topico + ": " + cortar(conteudo)), s);
                }
            }
            out.write(Protocolos.mqttDisconnect());
            return Resultado.falha(ms(t0), "nenhuma mensagem em " + topico + " em " + m.getTempoLimiteS() + " s");
        } catch (IOException e) {
            return Resultado.falha(ms(t0), Verificadores.erroRede(e));
        }
    }

    // ================================================================================== MongoDB

    Resultado mongodb(MonitorDO m, String senha, boolean tls) {
        int limite = Math.max(1, m.getTempoLimiteS());
        MongoClientSettings.Builder b = MongoClientSettings.builder()
                .applyToClusterSettings(c -> c.hosts(List.of(new ServerAddress(m.getAlvo(), m.getPorta()))).serverSelectionTimeout(limite, TimeUnit.SECONDS))
                .applyToSocketSettings(c -> c.connectTimeout(limite, TimeUnit.SECONDS).readTimeout(limite, TimeUnit.SECONDS))
                .applyToSslSettings(c -> {
                    c.enabled(tls).invalidHostNameAllowed(Boolean.TRUE.equals(m.getIgnorarTls()));
                    if (tls && Boolean.TRUE.equals(m.getIgnorarTls())) {
                        c.context(Verificadores.contextoSemVerificacao());
                    }
                })
                .applicationName("CO-TI-Monitor");
        if (m.getUsuario() != null) {
            b.credential(MongoCredential.createCredential(m.getUsuario(), Optional.ofNullable(m.getBanco()).orElse("admin"),
                    Optional.ofNullable(senha).orElse("").toCharArray()));
        }
        long t0 = System.nanoTime();
        try (MongoClient c = MongoClients.create(b.build())) {
            Document r = c.getDatabase(Optional.ofNullable(m.getBanco()).orElse("admin")).runCommand(new Document("ping", 1));
            Object ok = r.get("ok");
            return ok instanceof Number n && n.doubleValue() == 1 ? Resultado.ok(ms(t0), "MongoDB respondeu ao ping")
                    : Resultado.falha(ms(t0), "MongoDB respondeu sem ok: " + cortar(r.toJson()));
        } catch (MongoSecurityException e) {
            return Resultado.falha(ms(t0), "usuário ou senha recusados pelo MongoDB");
        } catch (MongoTimeoutException e) {
            return Resultado.falha(ms(t0), "sem conexão com o MongoDB em " + limite + " s");
        } catch (MongoException e) {
            return Resultado.falha(ms(t0), cortar(Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
        }
    }

    // ================================================================================== RabbitMQ

    Resultado rabbitmq(MonitorDO m, String senha) {
        String base = m.getAlvo().replaceAll("/+$", "");
        String auth = Base64.getEncoder().encodeToString((m.getUsuario() + ":" + Optional.ofNullable(senha).orElse("")).getBytes(StandardCharsets.UTF_8));
        HttpClient cliente = Boolean.TRUE.equals(m.getIgnorarTls()) ? Verificadores.HTTP_SEM_TLS : Verificadores.HTTP;
        long t0 = System.nanoTime();
        try {
            HttpResponse<String> r = cliente.send(HttpRequest.newBuilder(URI.create(base + "/api/health/checks/alarms"))
                    .timeout(Duration.ofSeconds(Math.max(1, m.getTempoLimiteS()))).header("Authorization", "Basic " + auth)
                    .header("User-Agent", "CO-TI-Monitor/1.0").GET().build(), HttpResponse.BodyHandlers.ofString());
            int c = r.statusCode();
            if (c == 200) {
                return Resultado.ok(ms(t0), "RabbitMQ sem alarmes");
            }
            if (c == 401 || c == 403) {
                return Resultado.falha(ms(t0), "usuário ou senha recusados pelo RabbitMQ (código " + c + ")");
            }
            if (c == 503) {
                return Resultado.falha(ms(t0), "RabbitMQ com alarme ativo (memória ou disco): " + cortar(r.body()));
            }
            return Resultado.falha(ms(t0), "a API do RabbitMQ respondeu " + c + (c == 404 ? " (o plugin de gerenciamento está ligado?)" : ""));
        } catch (HttpTimeoutException e) {
            return Resultado.falha("tempo esgotado (" + m.getTempoLimiteS() + " s)");
        } catch (IOException e) {
            return Resultado.falha(ms(t0), Verificadores.erroRede(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.falha("verificação interrompida");
        } catch (IllegalArgumentException e) {
            return Resultado.falha("URL inválida: " + e.getMessage());
        }
    }

}
