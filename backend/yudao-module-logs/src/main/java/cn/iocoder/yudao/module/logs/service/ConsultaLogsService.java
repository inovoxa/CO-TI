package cn.iocoder.yudao.module.logs.service;

import cn.iocoder.yudao.module.logs.controller.admin.vo.LogsVOs;
import cn.iocoder.yudao.module.logs.dominio.Consulta;
import cn.iocoder.yudao.module.logs.dominio.Fluxos;
import cn.iocoder.yudao.module.logs.dominio.Periodo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.logs.enums.ErrorCodeConstants.CAMPO_INVALIDO;
import static cn.iocoder.yudao.module.logs.enums.ErrorCodeConstants.CONSULTA_INVALIDA;

/**
 * Consultas dos painéis e da busca. O SQL é montado só com parâmetros (a {@link Consulta} traduz a linguagem de busca);
 * cada consulta tem limite de tempo, e o mesmo pedido feito em menos de 10 s (vários quadros, várias pessoas olhando o
 * mesmo painel) reaproveita o resultado.
 */
@Service
public class ConsultaLogsService {

    /** Acima disso a busca mostra "mais de N" em vez de contar tudo */
    static final long LIMITE_TOTAL = 1_000_000;
    private static final long CACHE_MS = 10_000;
    private static final long[] INTERVALOS = {60, 300, 600, 1800, 3600, 10_800, 21_600, 43_200, 86_400, 604_800};
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void iniciar() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(60);
    }

    // ================================================================================== consultas

    public LogsVOs.Mensagens mensagens(LogsVOs.MensagensReq req) {
        return cache("m", req, () -> {
            long ini = System.currentTimeMillis();
            Onde w = onde(req.getFiltro(), LocalDateTime.now());
            LogsVOs.Mensagens r = new LogsVOs.Mensagens();
            List<Object> p = new ArrayList<>(w.parametros);
            p.add(req.getTamanho());
            p.add((long) (req.getPagina() - 1) * req.getTamanho());
            r.setLista(jdbc.query("SELECT momento, fluxo, origem, ip, nivel, aplicacao, mensagem, campos::text AS campos "
                    + "FROM logs.mensagem WHERE " + w.sql + " ORDER BY momento DESC LIMIT ? OFFSET ?", (rs, i) -> {
                LogsVOs.Mensagem m = new LogsVOs.Mensagem();
                m.setMomento(rs.getTimestamp("momento").toLocalDateTime());
                m.setFluxo(rs.getString("fluxo"));
                m.setOrigem(rs.getString("origem"));
                m.setIp(rs.getString("ip"));
                m.setNivel(rs.getInt("nivel"));
                m.setAplicacao(rs.getString("aplicacao"));
                m.setMensagem(rs.getString("mensagem"));
                m.setCampos(mapa(rs.getString("campos")));
                return m;
            }, p.toArray()));
            List<Object> pc = new ArrayList<>(w.parametros);
            pc.add(LIMITE_TOTAL + 1);
            Long total = jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM logs.mensagem WHERE " + w.sql
                    + " LIMIT ?) x", Long.class, pc.toArray());
            r.setTotal(total == null ? 0 : total);
            r.setLimiteTotal(LIMITE_TOTAL);
            r.setMs(System.currentTimeMillis() - ini);
            return r;
        });
    }

    public LogsVOs.Contagem contagem(LogsVOs.ContagemReq req) {
        return cache("c", req, () -> {
            long ini = System.currentTimeMillis();
            LocalDateTime agora = LocalDateTime.now();
            Periodo periodo = periodo(req.getFiltro(), agora);
            String expr = "count(*)";
            List<Object> pe = List.of();
            if (temTexto(req.getSoma())) {
                Consulta.Sql s = soma(req.getSoma());
                expr = "COALESCE(sum(" + s.condicao() + "), 0)::bigint";
                pe = s.parametros();
            } else if (temTexto(req.getDistinto())) {
                Consulta.Sql c = campo(req.getDistinto());
                expr = "count(DISTINCT " + c.condicao() + ")";
                pe = c.parametros();
            }
            LogsVOs.Contagem r = new LogsVOs.Contagem();
            r.setValor(contar(expr, pe, onde(req.getFiltro(), periodo)));
            Periodo antes = periodo.anterior();
            if (antes != null) {
                r.setAnterior(contar(expr, pe, onde(req.getFiltro(), antes)));
            }
            r.setRotuloPeriodo(periodo.rotulo());
            r.setMs(System.currentTimeMillis() - ini);
            return r;
        });
    }

    public LogsVOs.Ranking ranking(LogsVOs.RankingReq req) {
        return cache("r", req, () -> {
            long ini = System.currentTimeMillis();
            Consulta.Sql c = campo(req.getCampo());
            Consulta.Sql s = temTexto(req.getSoma()) ? soma(req.getSoma()) : null;
            Onde w = onde(req.getFiltro(), LocalDateTime.now());
            List<Object> p = new ArrayList<>(c.parametros());
            if (s != null) {
                p.addAll(s.parametros());
            }
            p.addAll(w.parametros);
            p.addAll(c.parametros());
            p.add(req.getLimite());
            String n = s == null ? "count(*)" : "COALESCE(sum(" + s.condicao() + "), 0)::bigint";
            LogsVOs.Ranking r = new LogsVOs.Ranking();
            jdbc.query("SELECT valor, n, q, sum(n) OVER () AS total FROM (SELECT " + c.condicao() + " AS valor, " + n
                    + " AS n, count(*) AS q FROM logs.mensagem WHERE " + w.sql + " AND " + c.condicao()
                    + " IS NOT NULL GROUP BY 1) g ORDER BY n DESC, valor LIMIT ?", rs -> {
                LogsVOs.Item item = new LogsVOs.Item(rs.getString("valor"), rs.getLong("n"));
                if (s != null) {
                    item.setMensagens(rs.getLong("q"));
                }
                r.getItens().add(item);
                r.setTotal(rs.getLong("total"));
            }, p.toArray());
            r.setMs(System.currentTimeMillis() - ini);
            return r;
        });
    }

    public LogsVOs.Histograma histograma(LogsVOs.Filtro filtro) {
        return histograma(filtro, null);
    }

    /** @param soma campo numérico somado em cada barra ("bytes" = sentbyte + rcvdbyte); null = conta mensagens */
    public LogsVOs.Histograma histograma(LogsVOs.Filtro filtro, String soma) {
        Consulta.Sql s = temTexto(soma) ? soma(soma) : null;
        return cache(s == null ? "h" : "h" + soma.strip(), filtro, () -> {
            long ini = System.currentTimeMillis();
            LocalDateTime agora = LocalDateTime.now();
            Periodo periodo = periodo(filtro, agora);
            LocalDateTime de = periodo.de();
            if (de == null) {
                Timestamp min = jdbc.queryForObject("SELECT min(momento) FROM logs.mensagem", Timestamp.class);
                de = min == null ? agora.minusDays(1) : min.toLocalDateTime();
            }
            long segundos = Math.max(60, Duration.between(de, periodo.ate()).toSeconds());
            long intervalo = Arrays.stream(INTERVALOS).filter(i -> segundos / i <= 120).findFirst().orElse(604_800);
            Onde w = onde(filtro, new Periodo(de, periodo.ate(), periodo.rotulo(), periodo.passo()));
            List<Object> p = new ArrayList<>();
            p.add(intervalo + " seconds");
            if (s != null) {
                p.addAll(s.parametros());
            }
            p.addAll(w.parametros);
            String n = s == null ? "count(*)" : "COALESCE(sum(" + s.condicao() + "), 0)::bigint";
            Map<LocalDateTime, Long> barras = new HashMap<>();
            jdbc.query("SELECT time_bucket(CAST(? AS interval), momento, TIMESTAMP '1970-01-01') AS t, " + n + " AS n FROM logs.mensagem WHERE "
                    + w.sql + " GROUP BY 1", rs -> {
                barras.put(rs.getTimestamp("t").toLocalDateTime(), rs.getLong("n"));
            }, p.toArray());
            LogsVOs.Histograma h = new LogsVOs.Histograma();
            h.setIntervalo(intervalo);
            // barras vazias também aparecem (o eixo do tempo fica contínuo)
            // horário local "ingênuo", alinhado a 1970-01-01, igual ao time_bucket com essa origem
            long inicio = de.toEpochSecond(ZoneOffset.UTC) / intervalo * intervalo;
            LocalDateTime t = LocalDateTime.ofEpochSecond(inicio, 0, ZoneOffset.UTC);
            for (int i = 0; !t.isAfter(periodo.ate()) && i < 500; i++, t = t.plusSeconds(intervalo)) {
                h.getMomentos().add(t);
                h.getTotais().add(barras.getOrDefault(t, 0L));
            }
            h.setMs(System.currentTimeMillis() - ini);
            return h;
        });
    }

    /** Campos que aparecem nas mensagens mais recentes do filtro (para a lista lateral da busca). */
    public List<LogsVOs.Item> campos(LogsVOs.Filtro filtro) {
        return cache("f", filtro, () -> {
            Onde w = onde(filtro, LocalDateTime.now());
            List<LogsVOs.Item> out = new ArrayList<>();
            jdbc.query("SELECT k, count(*) AS n FROM (SELECT campos FROM logs.mensagem WHERE " + w.sql
                    + " ORDER BY momento DESC LIMIT 500) x, jsonb_object_keys(x.campos) AS k GROUP BY k ORDER BY n DESC, k LIMIT 150",
                    rs -> {
                        out.add(new LogsVOs.Item(rs.getString("k"), rs.getLong("n")));
                    }, w.parametros.toArray());
            return out;
        });
    }

    /** Quem mandou logs nas últimas 24 h. */
    public List<LogsVOs.Origem> origens() {
        return cache("o", "24h", () -> jdbc.query("SELECT origem, fluxo, max(ip) AS ip, count(*) AS n, max(momento) AS ultima "
                + "FROM logs.mensagem WHERE momento >= now()::timestamp - interval '1 day' GROUP BY origem, fluxo "
                + "ORDER BY n DESC LIMIT 500", (rs, i) -> {
            LogsVOs.Origem o = new LogsVOs.Origem();
            o.setOrigem(rs.getString("origem"));
            o.setFluxo(rs.getString("fluxo"));
            o.setIp(rs.getString("ip"));
            o.setTotal(rs.getLong("n"));
            o.setUltima(rs.getTimestamp("ultima").toLocalDateTime());
            return o;
        }));
    }

    public Map<String, Long> ultimas24hPorFluxo() {
        Map<String, Long> m = new LinkedHashMap<>();
        Fluxos.TODOS.forEach(f -> m.put(f, 0L));
        jdbc.query("SELECT fluxo, count(*) AS n FROM logs.mensagem WHERE momento >= now()::timestamp - interval '1 day' GROUP BY 1",
                rs -> {
                    m.put(rs.getString("fluxo"), rs.getLong("n"));
                });
        return m;
    }

    public String tamanho() {
        return jdbc.queryForObject("SELECT pg_size_pretty(hypertable_size('logs.mensagem'))", String.class);
    }

    // ================================================================================== montagem do SQL

    private record Onde(String sql, List<Object> parametros) {
    }

    private Onde onde(LogsVOs.Filtro f, LocalDateTime agora) {
        return onde(f, periodo(f, agora));
    }

    private Onde onde(LogsVOs.Filtro f, Periodo periodo) {
        StringBuilder sql = new StringBuilder();
        List<Object> p = new ArrayList<>();
        if (periodo.de() != null) {
            sql.append("momento >= ? AND ");
            p.add(Timestamp.valueOf(periodo.de()));
        }
        sql.append("momento <= ?");
        p.add(Timestamp.valueOf(periodo.ate()));
        if (f.getFluxo() != null && !f.getFluxo().isBlank()) {
            if (!Fluxos.TODOS.contains(f.getFluxo())) {
                throw exception(CONSULTA_INVALIDA, "fluxo desconhecido: " + f.getFluxo());
            }
            sql.append(" AND fluxo = ?");
            p.add(f.getFluxo());
        }
        for (String texto : new String[]{f.getConsulta(), f.getFiltroGeral()}) {
            if (texto != null && !texto.isBlank()) {
                Consulta.Sql c = traduzir(texto);
                sql.append(" AND (").append(c.condicao()).append(")");
                p.addAll(c.parametros());
            }
        }
        return new Onde(sql.toString(), p);
    }

    private static Periodo periodo(LogsVOs.Filtro f, LocalDateTime agora) {
        try {
            return Periodo.de(f.getPeriodo(), f.getDe(), f.getAte(), agora);
        } catch (IllegalArgumentException e) {
            throw exception(CONSULTA_INVALIDA, e.getMessage());
        }
    }

    private static Consulta.Sql traduzir(String texto) {
        try {
            return Consulta.traduzir(texto);
        } catch (IllegalArgumentException e) {
            throw exception(CONSULTA_INVALIDA, e.getMessage());
        }
    }

    private static Consulta.Sql campo(String nome) {
        try {
            return Consulta.campo(nome.strip());
        } catch (IllegalArgumentException e) {
            throw exception(CAMPO_INVALIDO, nome);
        }
    }

    /** Soma de "bytes" = enviados + recebidos (FortiGate) */
    public static final String BYTES = "bytes";

    /**
     * Valor numérico de um campo para somar: texto que não é número inteiro conta 0 (os campos chegam como texto do
     * log, e um valor estranho não pode derrubar a consulta). "bytes" = sentbyte + rcvdbyte.
     */
    private static Consulta.Sql soma(String nome) {
        String n = nome.strip();
        if (BYTES.equals(n)) {
            Consulta.Sql a = numero("sentbyte");
            Consulta.Sql b = numero("rcvdbyte");
            List<Object> p = new ArrayList<>(a.parametros());
            p.addAll(b.parametros());
            return new Consulta.Sql("(" + a.condicao() + " + " + b.condicao() + ")", p);
        }
        return numero(n);
    }

    private static Consulta.Sql numero(String nome) {
        if (Consulta.ehColuna(nome)) {
            throw exception(CAMPO_INVALIDO, nome);
        }
        Consulta.Sql c = campo(nome);
        List<Object> p = new ArrayList<>(c.parametros());
        p.addAll(c.parametros());
        return new Consulta.Sql("(CASE WHEN " + c.condicao() + " ~ '^[0-9]{1,18}$' THEN CAST(" + c.condicao()
                + " AS bigint) ELSE 0 END)", p);
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    private long contar(String expr, List<Object> pe, Onde w) {
        List<Object> p = new ArrayList<>(pe);
        p.addAll(w.parametros);
        Long n = jdbc.queryForObject("SELECT " + expr + " FROM logs.mensagem WHERE " + w.sql, Long.class, p.toArray());
        return n == null ? 0 : n;
    }

    private static Map<String, Object> mapa(String json) {
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ================================================================================== cache curto

    @SuppressWarnings("unchecked")
    private <T> T cache(String tipo, Object pedido, Supplier<T> calcular) {
        String chave;
        try {
            chave = tipo + JSON.writeValueAsString(pedido);
        } catch (Exception e) {
            return calcular.get();
        }
        long agora = System.currentTimeMillis();
        Object[] guardado = cache.get(chave);
        if (guardado != null && (long) guardado[0] > agora) {
            return (T) guardado[1];
        }
        T valor = calcular.get();
        if (cache.size() > 2000) {
            cache.entrySet().removeIf(e -> (long) e.getValue()[0] <= agora);
        }
        cache.put(chave, new Object[]{agora + CACHE_MS, valor});
        return valor;
    }

}
