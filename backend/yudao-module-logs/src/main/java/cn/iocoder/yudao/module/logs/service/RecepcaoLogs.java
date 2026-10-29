package cn.iocoder.yudao.module.logs.service;

import cn.iocoder.yudao.module.logs.config.LogsProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Entradas de rede: Syslog UDP e TCP (RFC 3164/5424, FortiGate, NXLog) e GELF UDP (com pedaços e compressão).
 * Cada entrada roda em sua própria thread e só enfileira o texto bruto no {@link GravadorLogs}.
 */
@Slf4j
@Component
public class RecepcaoLogs {

    /** Situação de uma entrada, para a tela de fontes. */
    public record Entrada(String nome, String protocolo, int porta, int portaPublica, boolean ativa, String erro,
                          long recebidas, long recusadas) {
    }

    private static final int MAXIMO_UDP = 65_535;
    private static final int MAXIMO_TCP_LINHA = 1_048_576;

    @Resource
    private LogsProperties props;
    @Resource
    private GravadorLogs gravador;

    private final List<Closeable> abertos = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, AtomicLong> recebidas = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> recusadas = new ConcurrentHashMap<>();
    private final Map<String, String> erros = new ConcurrentHashMap<>();
    private final Map<String, Boolean> ativas = new ConcurrentHashMap<>();
    private List<Rede> permitidos = List.of();
    private volatile boolean rodando;
    /** GELF em pedaços: id da mensagem → pedaços recebidos (descartados se incompletos em 5 s, como no Graylog) */
    private final Map<String, Pedacos> pedacos = new ConcurrentHashMap<>();

    @PostConstruct
    void iniciar() {
        if (!props.isRecepcao()) {
            log.info("[logs] recepção desligada (coti.logs.recepcao=false)");
            return;
        }
        permitidos = props.getPermitidos().stream().filter(s -> !s.isBlank()).map(Rede::de).toList();
        rodando = true;
        if (props.getSyslogUdpPorta() > 0) {
            iniciarUdp("syslog-udp", props.getSyslogUdpPorta(), "syslog");
        }
        if (props.getGelfUdpPorta() > 0) {
            iniciarUdp("gelf-udp", props.getGelfUdpPorta(), "gelf");
        }
        if (props.getSyslogTcpPorta() > 0) {
            iniciarTcp("syslog-tcp", props.getSyslogTcpPorta());
        }
    }

    @PreDestroy
    void parar() {
        rodando = false;
        synchronized (abertos) {
            abertos.forEach(c -> {
                try {
                    c.close();
                } catch (IOException ignorar) {
                    // saindo
                }
            });
        }
    }

    public List<Entrada> entradas() {
        List<Entrada> out = new ArrayList<>();
        out.add(entrada("syslog-udp", "Syslog", "UDP", props.getSyslogUdpPorta(), props.getPortaPublicaSyslog()));
        out.add(entrada("syslog-tcp", "Syslog", "TCP", props.getSyslogTcpPorta(), props.getPortaPublicaSyslog()));
        out.add(entrada("gelf-udp", "GELF", "UDP", props.getGelfUdpPorta(), props.getPortaPublicaGelf()));
        return out;
    }

    private Entrada entrada(String chave, String nome, String protocolo, int porta, int portaPublica) {
        return new Entrada(nome, protocolo, porta, portaPublica, Boolean.TRUE.equals(ativas.get(chave)), erros.get(chave),
                contador(recebidas, chave).get(), contador(recusadas, chave).get());
    }

    // ================================================================================== UDP

    private void iniciarUdp(String chave, int porta, String formato) {
        try {
            DatagramSocket s = new DatagramSocket(porta);
            s.setReceiveBufferSize(8 * 1024 * 1024);
            abertos.add(s);
            ativas.put(chave, true);
            Thread t = new Thread(() -> lacoUdp(chave, s, formato), "logs-" + chave);
            t.setDaemon(true);
            t.start();
            log.info("[logs] {} ouvindo na porta {}", chave, porta);
        } catch (IOException e) {
            erros.put(chave, e.getMessage());
            log.error("[logs] não abriu {} na porta {}: {}", chave, porta, e.getMessage());
        }
    }

    private void lacoUdp(String chave, DatagramSocket s, String formato) {
        byte[] buf = new byte[MAXIMO_UDP];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (rodando) {
            try {
                s.receive(p);
                String ip = p.getAddress().getHostAddress();
                if (!permitido(p.getAddress())) {
                    contador(recusadas, chave).incrementAndGet();
                    continue;
                }
                byte[] dados = Arrays.copyOfRange(p.getData(), p.getOffset(), p.getOffset() + p.getLength());
                if (formato.equals("gelf")) {
                    dados = juntarPedacos(dados, ip);
                    if (dados == null) {
                        continue;
                    }
                    dados = descomprimir(dados);
                }
                contador(recebidas, chave).incrementAndGet();
                gravador.enfileirar(new GravadorLogs.Bruta(formato, new String(dados, StandardCharsets.UTF_8), ip,
                        LocalDateTime.now()));
            } catch (SocketException e) {
                if (rodando) {
                    log.warn("[logs] {}: {}", chave, e.getMessage());
                }
            } catch (Exception e) {
                log.debug("[logs] {}: mensagem ignorada: {}", chave, e.getMessage());
            }
        }
    }

    /** GELF em pedaços: 0x1e 0x0f, 8 bytes de id, número do pedaço, total de pedaços, dados. */
    private byte[] juntarPedacos(byte[] d, String ip) {
        if (d.length < 12 || d[0] != 0x1e || d[1] != 0x0f) {
            return d;
        }
        String id = ip + ":" + HexFormat.of().formatHex(d, 2, 10);
        int n = d[10] & 0xff;
        int total = d[11] & 0xff;
        if (total == 0 || total > 128 || n >= total) {
            return null;
        }
        long agora = System.currentTimeMillis();
        pedacos.values().removeIf(x -> agora - x.inicio > 5000);
        Pedacos ps = pedacos.computeIfAbsent(id, k -> new Pedacos(total, agora));
        synchronized (ps) {
            ps.partes[n] = Arrays.copyOfRange(d, 12, d.length);
            if (++ps.recebidos < total) {
                return null;
            }
        }
        pedacos.remove(id);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] parte : ps.partes) {
            if (parte == null) {
                return null;
            }
            out.writeBytes(parte);
        }
        return out.toByteArray();
    }

    private static final class Pedacos {
        final byte[][] partes;
        final long inicio;
        int recebidos;

        Pedacos(int total, long inicio) {
            this.partes = new byte[total][];
            this.inicio = inicio;
        }
    }

    /** GELF pode vir em gzip (1f 8b) ou zlib (78 ..). Limite de 8 MB descomprimido. */
    private static byte[] descomprimir(byte[] d) throws IOException {
        InputStream in;
        if (d.length > 2 && (d[0] & 0xff) == 0x1f && (d[1] & 0xff) == 0x8b) {
            in = new GZIPInputStream(new ByteArrayInputStream(d));
        } else if (d.length > 2 && (d[0] & 0xff) == 0x78) {
            in = new InflaterInputStream(new ByteArrayInputStream(d));
        } else {
            return d;
        }
        try (in) {
            byte[] out = in.readNBytes(8 * 1024 * 1024);
            return out;
        }
    }

    // ================================================================================== TCP

    private void iniciarTcp(String chave, int porta) {
        try {
            ServerSocket ss = new ServerSocket(porta);
            abertos.add(ss);
            ativas.put(chave, true);
            Thread t = new Thread(() -> {
                while (rodando) {
                    try {
                        Socket s = ss.accept();
                        if (!permitido(s.getInetAddress())) {
                            contador(recusadas, chave).incrementAndGet();
                            s.close();
                            continue;
                        }
                        Thread.ofVirtual().name("logs-tcp-" + s.getInetAddress().getHostAddress()).start(() -> conexaoTcp(chave, s));
                    } catch (IOException e) {
                        if (rodando) {
                            log.warn("[logs] {}: {}", chave, e.getMessage());
                        }
                    }
                }
            }, "logs-" + chave);
            t.setDaemon(true);
            t.start();
            log.info("[logs] {} ouvindo na porta {}", chave, porta);
        } catch (IOException e) {
            erros.put(chave, e.getMessage());
            log.error("[logs] não abriu {} na porta {}: {}", chave, porta, e.getMessage());
        }
    }

    /**
     * Uma conexão TCP: aceita as duas formas de separar mensagens do RFC 6587, contagem de bytes ("123 &lt;14&gt;...")
     * e quebra de linha.
     */
    private void conexaoTcp(String chave, Socket s) {
        String ip = s.getInetAddress().getHostAddress();
        abertos.add(s);
        try (s; BufferedInputStream in = new BufferedInputStream(s.getInputStream(), 65_536)) {
            s.setSoTimeout(10 * 60 * 1000);
            while (rodando) {
                in.mark(12);
                int primeiro = in.read();
                if (primeiro < 0) {
                    return;
                }
                byte[] msg;
                if (primeiro >= '1' && primeiro <= '9') {
                    // pode ser contagem de bytes: dígitos, espaço, mensagem
                    int tamanho = primeiro - '0';
                    int c;
                    int lidos = 1;
                    while ((c = in.read()) >= '0' && c <= '9' && lidos < 9) {
                        tamanho = tamanho * 10 + (c - '0');
                        lidos++;
                    }
                    if (c == ' ' && tamanho <= MAXIMO_TCP_LINHA) {
                        msg = in.readNBytes(tamanho);
                    } else {
                        in.reset();
                        msg = linha(in);
                    }
                } else {
                    in.reset();
                    msg = linha(in);
                }
                if (msg == null) {
                    return;
                }
                if (msg.length > 0) {
                    contador(recebidas, chave).incrementAndGet();
                    gravador.enfileirarEsperando(new GravadorLogs.Bruta("syslog", new String(msg, StandardCharsets.UTF_8),
                            ip, LocalDateTime.now()));
                }
            }
        } catch (IOException e) {
            log.debug("[logs] conexão de {} encerrada: {}", ip, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            abertos.remove(s);
        }
    }

    private static byte[] linha(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(512);
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n' || c == 0) {
                return b.toByteArray();
            }
            if (b.size() < MAXIMO_TCP_LINHA) {
                b.write(c);
            }
        }
        return b.size() > 0 ? b.toByteArray() : null;
    }

    // ================================================================================== apoio

    private boolean permitido(InetAddress a) {
        return permitidos.isEmpty() || permitidos.stream().anyMatch(r -> r.contem(a));
    }

    private static AtomicLong contador(Map<String, AtomicLong> m, String chave) {
        return m.computeIfAbsent(chave, k -> new AtomicLong());
    }

    /** Endereço ou rede no formato CIDR (10.0.0.0/8, 192.168.1.10). */
    record Rede(byte[] base, int bits) {

        static Rede de(String s) {
            try {
                String[] p = s.strip().split("/");
                byte[] base = InetAddress.getByName(p[0]).getAddress();
                return new Rede(base, p.length > 1 ? Integer.parseInt(p[1]) : base.length * 8);
            } catch (Exception e) {
                throw new IllegalArgumentException("coti.logs.permitidos: endereço inválido " + s, e);
            }
        }

        boolean contem(InetAddress a) {
            byte[] x = a.getAddress();
            if (x.length != base.length) {
                return false;
            }
            int resto = bits;
            for (int i = 0; i < x.length && resto > 0; i++, resto -= 8) {
                int mascara = resto >= 8 ? 0xff : (0xff << (8 - resto)) & 0xff;
                if ((x[i] & mascara) != (base[i] & mascara)) {
                    return false;
                }
            }
            return true;
        }
    }

}
