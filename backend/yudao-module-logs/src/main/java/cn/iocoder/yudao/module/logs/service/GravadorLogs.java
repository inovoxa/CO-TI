package cn.iocoder.yudao.module.logs.service;

import cn.iocoder.yudao.module.logs.config.LogsProperties;
import cn.iocoder.yudao.module.logs.dominio.Interpretador;
import cn.iocoder.yudao.module.logs.dominio.Mensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fila entre a rede e o banco: a recepção só enfileira o texto bruto; algumas threads interpretam e gravam em lotes
 * pelo COPY do PostgreSQL. Fila cheia: no UDP a mensagem nova é descartada e contada; no TCP a recepção espera, e o
 * equipamento que manda diminui o ritmo (por isso o TCP é o indicado para volume alto).
 */
@Slf4j
@Component
public class GravadorLogs {

    /** O que chegou pela rede, ainda sem interpretar. */
    public record Bruta(String formato, String texto, String ip, LocalDateTime recebido) {
    }

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String COPY = "COPY logs.mensagem (momento, recebido, fluxo, origem, ip, nivel, aplicacao, "
            + "mensagem, campos) FROM STDIN";

    @Resource
    private LogsProperties props;
    @Resource
    private DataSource dataSource;

    private BlockingQueue<Bruta> fila;
    private final List<Thread> threads = new ArrayList<>();
    private final AtomicLong ultimoAviso = new AtomicLong();
    private volatile boolean rodando;
    private final ZoneId fuso = ZoneId.systemDefault();

    final AtomicLong gravadas = new AtomicLong();
    final AtomicLong descartadas = new AtomicLong();
    final AtomicLong invalidas = new AtomicLong();
    final AtomicLong falhas = new AtomicLong();

    @PostConstruct
    void iniciar() {
        fila = new ArrayBlockingQueue<>(Math.max(1000, props.getFilaMaxima()));
        rodando = true;
        for (int i = 1; i <= Math.max(1, props.getGravadores()); i++) {
            Thread t = new Thread(this::laco, "logs-gravador-" + i);
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
    }

    @PreDestroy
    void parar() throws InterruptedException {
        rodando = false;
        for (Thread t : threads) {
            t.interrupt();
        }
        for (Thread t : threads) {
            t.join(5000);
        }
    }

    /** UDP: com a fila cheia, descarta (não há como pedir ao equipamento que espere). */
    public void enfileirar(Bruta b) {
        if (!fila.offer(b)) {
            descartar();
        }
    }

    /** TCP: com a fila cheia, espera até 30 s (a conexão segura o equipamento) antes de descartar. */
    public void enfileirarEsperando(Bruta b) throws InterruptedException {
        if (!fila.offer(b, 30, TimeUnit.SECONDS)) {
            descartar();
        }
    }

    private void descartar() {
        long n = descartadas.incrementAndGet();
        long agora = System.currentTimeMillis();
        long antes = ultimoAviso.get();
        if (agora - antes > 60_000 && ultimoAviso.compareAndSet(antes, agora)) {
            log.warn("[logs] fila cheia ({} mensagens): {} descartadas até agora. Aumente coti.logs.fila-maxima "
                    + "ou coti.logs.gravadores, ou mande por TCP", fila.size(), n);
        }
    }

    public int pendentes() {
        return fila.size();
    }

    /** Desde que o backend subiu. */
    public record Contadores(long gravadas, long descartadas, long invalidas, long falhas) {
    }

    public Contadores contadores() {
        return new Contadores(gravadas.get(), descartadas.get(), invalidas.get(), falhas.get());
    }

    private void laco() {
        List<Bruta> brutas = new ArrayList<>(props.getLote());
        while (rodando || !fila.isEmpty()) {
            try {
                Bruta primeira = fila.poll(1, TimeUnit.SECONDS);
                if (primeira == null) {
                    continue;
                }
                brutas.add(primeira);
                fila.drainTo(brutas, props.getLote() - 1);
                gravar(interpretar(brutas));
            } catch (InterruptedException e) {
                if (!rodando) {
                    // drena o que sobrou antes de sair
                    fila.drainTo(brutas);
                    gravar(interpretar(brutas));
                    return;
                }
            } catch (Exception e) {
                falhas.addAndGet(brutas.size());
                log.error("[logs] falha ao gravar {} mensagens", brutas.size(), e);
            } finally {
                brutas.clear();
            }
        }
    }

    private List<Mensagem> interpretar(List<Bruta> brutas) {
        List<Mensagem> out = new ArrayList<>(brutas.size());
        for (Bruta b : brutas) {
            try {
                out.add(b.formato().equals("gelf")
                        ? Interpretador.gelf(b.texto(), b.ip(), b.recebido(), fuso, props.getMensagemMaxima())
                        : Interpretador.syslog(b.texto(), b.ip(), b.recebido(), fuso, props.getMensagemMaxima()));
            } catch (Exception e) {
                invalidas.incrementAndGet();
            }
        }
        return out;
    }

    /** Grava pelo COPY (formato texto do PostgreSQL). Usado também pelos testes. */
    void gravar(List<Mensagem> mensagens) {
        if (mensagens.isEmpty()) {
            return;
        }
        StringBuilder b = new StringBuilder(mensagens.size() * 400);
        for (Mensagem m : mensagens) {
            b.append(DATA.format(m.momento())).append('\t')
                    .append(DATA.format(m.recebido())).append('\t');
            campo(b, m.fluxo()).append('\t');
            campo(b, m.origem()).append('\t');
            campo(b, m.ip()).append('\t');
            b.append(m.nivel()).append('\t');
            campo(b, m.aplicacao()).append('\t');
            campo(b, m.mensagem()).append('\t');
            try {
                // o jsonb recusa \u0000
                campo(b, JSON.writeValueAsString(m.campos()).replace("\\u0000", ""));
            } catch (Exception e) {
                b.append("{}");
            }
            b.append('\n');
        }
        Connection c = DataSourceUtils.getConnection(dataSource);
        try {
            long n = c.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY, new StringReader(b.toString()));
            gravadas.addAndGet(n);
        } catch (Exception e) {
            throw new IllegalStateException("COPY falhou: " + e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(c, dataSource);
        }
    }

    /** Escapa para o formato texto do COPY; null vira \N. */
    private static StringBuilder campo(StringBuilder b, String v) {
        if (v == null) {
            return b.append("\\N");
        }
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            switch (ch) {
                case '\\' -> b.append("\\\\");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\0' -> { } // o PostgreSQL não guarda o caractere nulo
                default -> b.append(ch);
            }
        }
        return b;
    }

}
