package cn.iocoder.yudao.module.monitoramento.service.verificador;

import cn.iocoder.yudao.module.monitoramento.dal.dataobject.KumaServidorDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dominio.Estados;
import cn.iocoder.yudao.module.monitoramento.service.SegredosMonitor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lê o estado dos monitores de um Uptime Kuma pelo {@code /metrics} (formato do Prometheus), que existe desde o Kuma
 * 1.x e não muda a cada versão como a API interna (Socket.IO). Conferido no {@code server/prometheus.js} do Kuma 2.5.7
 * (MIT): {@code monitor_status} (0 fora, 1 no ar, 2 pendente, 3 manutenção), {@code monitor_response_time} (ms),
 * {@code monitor_cert_days_remaining}, com os rótulos {@code monitor_id} (2.x), {@code monitor_name},
 * {@code monitor_type}, {@code monitor_url}, {@code monitor_hostname} e {@code monitor_port}.
 * <p>
 * Uma leitura serve a todos os monitores do mesmo Kuma por {@link #VALIDADE_MS}: o Kuma limita a autenticação da API
 * (60 por minuto) e o /metrics traz todos os monitores de uma vez.
 */
@Component
public class ClienteKuma {

    static final long VALIDADE_MS = 15_000;
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    @Resource
    private SegredosMonitor segredosMonitor;

    /** Um monitor como o Kuma informa. {@code id} é null no Kuma 1.x (não tem o rótulo monitor_id). */
    public record MonitorKuma(String id, String nome, String tipo, String url, String host, String porta, Integer estado,
                              Integer latenciaMs, Integer certDias) {

        /** O que mostrar como alvo: a URL, ou host:porta. */
        public String alvo() {
            if (url != null && !url.isBlank() && !"https://".equals(url) && !"null".equals(url)) {
                return url;
            }
            if (host == null || host.isBlank() || "null".equals(host)) {
                return null;
            }
            return host + (porta != null && !porta.isBlank() && !"null".equals(porta) ? ":" + porta : "");
        }
    }

    private record Leitura(String impressao, long em, List<MonitorKuma> monitores) {
    }

    private final Map<Long, Leitura> leituras = new ConcurrentHashMap<>();
    private final Map<Long, Object> travas = new ConcurrentHashMap<>();

    public void esquecer(Long id) {
        if (id != null) {
            leituras.remove(id);
        }
    }

    /** Todos os monitores que o Kuma informa (os pausados no Kuma não aparecem). Erro vem como IllegalStateException. */
    public List<MonitorKuma> monitores(KumaServidorDO s, boolean novaLeitura) {
        String impressao = Objects.hash(s.getUrl(), s.getUsuario(), s.getChave(), s.getIgnorarTls()) + "";
        if (s.getId() == null) {
            return ler(s);
        }
        synchronized (travas.computeIfAbsent(s.getId(), k -> new Object())) {
            Leitura l = leituras.get(s.getId());
            if (!novaLeitura && l != null && l.impressao().equals(impressao) && System.currentTimeMillis() - l.em() < VALIDADE_MS) {
                return l.monitores();
            }
            List<MonitorKuma> lidos = ler(s);
            leituras.put(s.getId(), new Leitura(impressao, System.currentTimeMillis(), lidos));
            return lidos;
        }
    }

    /** Estado de um monitor do Kuma, espelhado como veio. */
    public Resultado estado(KumaServidorDO s, MonitorDO m) {
        // sem o Kuma não dá para saber: fica pendente (sem abrir chamado de todos os monitores de uma vez); o que já
        // estava fora continua fora, para não avisar de novo a mesma queda quando o Kuma voltar
        int incerto = Objects.equals(m.getEstado(), Estados.FORA) ? Estados.FORA : Estados.PENDENTE;
        List<MonitorKuma> lista;
        try {
            lista = monitores(s, false);
        } catch (IllegalStateException e) {
            return Resultado.espelho(incerto, null, "sem resposta do Kuma " + s.getNome() + ": " + e.getMessage());
        }
        MonitorKuma k = achar(lista, m.getConsulta());
        if (k == null) {
            return Resultado.espelho(incerto, null, "o monitor não aparece no Kuma " + s.getNome()
                    + ": está pausado ou foi excluído lá");
        }
        if (k.estado() == null || k.estado() < 0 || k.estado() > 3) {
            return Resultado.espelho(incerto, null, "o Kuma ainda não verificou este monitor");
        }
        Integer lat = k.estado() == Estados.NO_AR ? k.latenciaMs() : null;
        Resultado r = Resultado.espelho(k.estado(), lat, switch (k.estado()) {
            case Estados.NO_AR -> "no ar no Kuma";
            case Estados.FORA -> "fora do ar no Kuma";
            case Estados.PENDENTE -> "pendente no Kuma (tentando de novo)";
            default -> "em manutenção no Kuma";
        });
        if (k.certDias() != null && k.certDias() >= 0) {
            r = r.comCertificado(k.certDias(), validade(m.getCertValidade(), k.certDias()));
        }
        return r;
    }

    /**
     * O Kuma só informa os dias que faltam (arredondados): a data calculada oscila um dia para lá e para cá, e cada
     * data nova seria um aviso novo. Até um dia de diferença, fica a data já guardada.
     */
    static LocalDateTime validade(LocalDateTime guardada, int dias) {
        LocalDateTime calculada = LocalDate.now().plusDays(dias).atStartOfDay();
        if (guardada != null && Math.abs(ChronoUnit.HOURS.between(guardada, calculada)) <= 24) {
            return guardada;
        }
        return calculada;
    }

    /** Pelo id do Kuma (2.x); sem id (1.x) ou sem achar, pelo nome. */
    public static MonitorKuma achar(List<MonitorKuma> lista, String chave) {
        if (chave == null || chave.isBlank()) {
            return null;
        }
        String c = chave.trim();
        for (MonitorKuma k : lista) {
            if (c.equals(k.id())) {
                return k;
            }
        }
        for (MonitorKuma k : lista) {
            if (k.nome() != null && k.nome().trim().equalsIgnoreCase(c)) {
                return k;
            }
        }
        return null;
    }

    private List<MonitorKuma> ler(KumaServidorDO s) {
        URI uri;
        try {
            uri = URI.create(s.getUrl().replaceAll("/+$", "") + "/metrics");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("endereço inválido");
        }
        String chave = s.getChave() == null ? null : s.getChave().startsWith(SegredosMonitor.CLARO) ? s.getChave().substring(SegredosMonitor.CLARO.length())
                : segredosMonitor.lerChaveKuma(s.getChave());
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("User-Agent", "CO-TI-Monitor/1.0")
                .header("Accept", "text/plain").GET();
        if (chave != null) {
            // com chave de API o Kuma ignora o usuário; sem ela, é o login comum
            String par = Optional.ofNullable(s.getUsuario()).orElse("") + ":" + chave;
            b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(par.getBytes(StandardCharsets.UTF_8)));
        }
        HttpClient cliente = Boolean.TRUE.equals(s.getIgnorarTls()) ? Verificadores.HTTP_SEM_TLS : Verificadores.HTTP;
        HttpResponse<byte[]> resp;
        try {
            resp = cliente.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("tempo esgotado (20 s)");
        } catch (IOException e) {
            throw new IllegalStateException(Verificadores.erroRede(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("leitura interrompida");
        }
        int c = resp.statusCode();
        if (c == 401 || c == 403) {
            throw new IllegalStateException("o Kuma recusou a chave de API (código " + c + "): confira a chave em Configurações → Chaves de API");
        }
        if (c == 404) {
            throw new IllegalStateException("o endereço não tem /metrics: confira se é o endereço do Uptime Kuma");
        }
        if (c == 429) {
            throw new IllegalStateException("o Kuma limitou as leituras (muitas em pouco tempo): tente em um minuto");
        }
        if (c / 100 != 2) {
            throw new IllegalStateException("o Kuma respondeu " + c);
        }
        if (resp.body().length > MAX_BYTES) {
            throw new IllegalStateException("resposta grande demais");
        }
        List<MonitorKuma> lista = interpretar(new String(resp.body(), StandardCharsets.UTF_8));
        if (lista.isEmpty() && !new String(resp.body(), StandardCharsets.UTF_8).contains("monitor_status")) {
            throw new IllegalStateException("a resposta não tem as métricas do Uptime Kuma (monitor_status)");
        }
        return lista;
    }

    /** Texto do Prometheus para a lista de monitores, na ordem em que aparecem. */
    static List<MonitorKuma> interpretar(String texto) {
        Map<String, Map<String, String>> rotulos = new LinkedHashMap<>();
        Map<String, Map<String, Double>> valores = new HashMap<>();
        for (String linha : texto.split("\n")) {
            String l = linha.trim();
            if (l.isEmpty() || l.startsWith("#") || !l.startsWith("monitor_")) {
                continue;
            }
            int chave = l.indexOf('{');
            if (chave < 0) {
                continue;
            }
            String nome = l.substring(0, chave);
            if (!Set.of("monitor_status", "monitor_response_time", "monitor_cert_days_remaining").contains(nome)) {
                continue;
            }
            Map<String, String> r = new HashMap<>();
            int fim = lerRotulos(l, chave + 1, r);
            if (fim < 0) {
                continue;
            }
            String[] resto = l.substring(fim + 1).trim().split("\\s+");
            Double v;
            try {
                v = Double.valueOf(resto[0]);
            } catch (NumberFormatException e) {
                continue;
            }
            String id = r.get("monitor_id");
            String k = id != null ? "id:" + id : "nome:" + r.getOrDefault("monitor_name", "");
            rotulos.putIfAbsent(k, r);
            valores.computeIfAbsent(k, x -> new HashMap<>()).put(nome, v);
        }
        List<MonitorKuma> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> e : rotulos.entrySet()) {
            Map<String, String> r = e.getValue();
            Map<String, Double> v = valores.get(e.getKey());
            Double status = v.get("monitor_status");
            Double lat = v.get("monitor_response_time");
            Double cert = v.get("monitor_cert_days_remaining");
            out.add(new MonitorKuma(r.get("monitor_id"), r.get("monitor_name"), r.get("monitor_type"), r.get("monitor_url"),
                    r.get("monitor_hostname"), r.get("monitor_port"), status == null || status.isNaN() ? null : (int) Math.round(status),
                    lat == null || lat.isNaN() || lat < 0 ? null : (int) Math.round(lat),
                    cert == null || cert.isNaN() ? null : (int) Math.floor(cert)));
        }
        return out;
    }

    /** Lê {@code a="x",b="y\"z"} a partir de {@code i}; devolve a posição do '}' ou -1. */
    private static int lerRotulos(String l, int i, Map<String, String> out) {
        int n = l.length();
        while (i < n) {
            char ch = l.charAt(i);
            if (ch == '}') {
                return i;
            }
            if (ch == ',' || ch == ' ') {
                i++;
                continue;
            }
            int igual = l.indexOf('=', i);
            if (igual < 0 || igual + 1 >= n || l.charAt(igual + 1) != '"') {
                return -1;
            }
            String nome = l.substring(i, igual).trim();
            StringBuilder v = new StringBuilder();
            int j = igual + 2;
            for (; j < n; j++) {
                char c = l.charAt(j);
                if (c == '\\' && j + 1 < n) {
                    char p = l.charAt(++j);
                    v.append(p == 'n' ? '\n' : p);
                } else if (c == '"') {
                    break;
                } else {
                    v.append(c);
                }
            }
            if (j >= n) {
                return -1;
            }
            out.put(nome, v.toString());
            i = j + 1;
        }
        return -1;
    }

}
