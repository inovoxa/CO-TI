package cn.iocoder.yudao.module.infraestrutura.service;

import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.InfraVOs;
import cn.iocoder.yudao.module.infraestrutura.dominio.Previsao;
import cn.iocoder.yudao.module.system.service.retencao.AvisoInfraService;
import cn.iocoder.yudao.module.system.service.retencao.RetencaoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Quanto cada área ocupa, quanto cresce e quando o espaço acaba. */
@Slf4j
@Service
public class CapacidadeService {

    private static final long GB = 1024L * 1024 * 1024;
    private static final int DIAS_DE_HISTORICO = 400;
    private static final int JANELA_DIAS = 30;
    /** Pasta dos anexos, relativa ao diretório da aplicação. */
    private static final String ARQUIVOS = "arquivos";
    /** Pasta dos logs de arquivo do backend: mesmo diretório do logging.file.name. */
    private static final String LOGS_ARQUIVO = "logs";

    private static final Map<String, String> ROTULOS = new LinkedHashMap<>();

    static {
        ROTULOS.put("logs", "Logs centralizados");
        ROTULOS.put("monitoramento", "Monitoramento");
        ROTULOS.put("chamados", "Chamados e catálogo");
        ROTULOS.put("arquivos", "Anexos e arquivos");
        ROTULOS.put("atendimento", "Atendimento");
        ROTULOS.put("chat", "Chat interno");
        ROTULOS.put("inventario", "Inventário");
        ROTULOS.put("sino", "Avisos do sino");
        ROTULOS.put("registros", "Registros técnicos");
        ROTULOS.put("logs-arquivo", "Logs de arquivo do backend");
        ROTULOS.put("sistema", "Sistema e demais dados");
    }

    /** O tamanho das hypertables fica nos pedaços, em outro schema: elas são somadas à parte. */
    private static final String SQL_AREAS = """
            SELECT CASE WHEN n.nspname = 'itsm' THEN 'chamados'
                        WHEN n.nspname = 'atendimento' THEN 'atendimento'
                        WHEN n.nspname = 'chat' THEN 'chat'
                        WHEN n.nspname = 'cmdb' THEN 'inventario'
                        WHEN n.nspname = 'monitor' THEN 'monitoramento'
                        WHEN n.nspname = 'logs' THEN 'logs'
                        WHEN c.relname = 'system_notify_message' THEN 'sino'
                        WHEN c.relname IN ('system_operate_log', 'system_login_log', 'infra_api_access_log', 'infra_api_error_log',
                                           'infra_job_log', 'system_oauth2_access_token', 'system_oauth2_refresh_token') THEN 'registros'
                        ELSE 'sistema' END AS area,
                   sum(pg_total_relation_size(c.oid))::bigint AS bytes,
                   sum(greatest(c.reltuples, 0))::bigint AS linhas
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r' AND n.nspname IN ('public', 'itsm', 'atendimento', 'chat', 'cmdb', 'monitor', 'logs', 'ia',
                                                    'identidade', 'cofre', 'portal')
            GROUP BY 1""";

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private RetencaoService retencao;
    @Resource
    private AvisoInfraService avisoInfra;

    public InfraVOs.Capacidade obter() {
        LocalDate hoje = LocalDate.now();
        if (!temMedida(hoje)) {
            registrarHoje();
        }
        Map<String, Medida> atuais = medidasDe(hoje);
        Map<String, Medida> primeiras = new HashMap<>();
        Map<String, LocalDate> dias = new HashMap<>();
        jdbcTemplate.query("SELECT DISTINCT ON (area) area, dia, bytes, linhas FROM infra_capacidade_dia WHERE dia >= ? ORDER BY area, dia",
                rs -> {
                    primeiras.put(rs.getString("area"), new Medida(rs.getLong("bytes"), rs.getLong("linhas")));
                    dias.put(rs.getString("area"), rs.getDate("dia").toLocalDate());
                }, Date.valueOf(hoje.minusDays(JANELA_DIAS)));

        List<InfraVOs.Area> areas = new ArrayList<>();
        long crescimentoTotal = 0;
        long totalBytes = 0;
        for (Map.Entry<String, String> r : ROTULOS.entrySet()) {
            Medida m = atuais.getOrDefault(r.getKey(), new Medida(0, 0));
            Medida p = primeiras.getOrDefault(r.getKey(), m);
            long n = ChronoUnit.DAYS.between(dias.getOrDefault(r.getKey(), hoje), hoje);
            long crescimento = Previsao.crescimentoPorDia(p.bytes(), m.bytes(), n);
            crescimentoTotal += crescimento;
            totalBytes += m.bytes();
            areas.add(new InfraVOs.Area(r.getKey(), r.getValue(), m.bytes(), m.linhas(), crescimento, guarda(r.getKey())));
        }
        Previsao.Disco d = disco();
        long diasDeHistorico = dias.values().stream().mapToLong(x -> ChronoUnit.DAYS.between(x, hoje)).max().orElse(0);
        return new InfraVOs.Capacidade(d.totalBytes(), d.usadoBytes(), d.livreBytes(), d.percentual(), d.pelaReserva(),
                totalBytes, crescimentoTotal, Previsao.diasAteEncher(d.livreBytes(), crescimentoTotal), diasDeHistorico,
                Previsao.DIAS_MINIMOS, areas);
    }

    /** Atualiza a medida do dia. A pasta de arquivos pode ser grande: é percorrida só na primeira medida de cada dia. */
    public void registrarHoje() {
        LocalDate hoje = LocalDate.now();
        Map<String, Medida> medidas = new HashMap<>();
        jdbcTemplate.query(SQL_AREAS, rs -> {
            medidas.put(rs.getString("area"), new Medida(rs.getLong("bytes"), rs.getLong("linhas")));
        });
        somarHypertable(medidas, "logs", "logs.mensagem");
        somarHypertable(medidas, "monitoramento", "monitor.batida");
        Medida arquivosDeHoje = medidasDe(hoje).get("arquivos");
        medidas.put("arquivos", arquivosDeHoje != null ? arquivosDeHoje : new Medida(tamanhoDaPasta(ARQUIVOS), 0));
        medidas.put("logs-arquivo", new Medida(tamanhoDaPasta(LOGS_ARQUIVO), 0));
        medidas.forEach((area, m) -> jdbcTemplate.update("INSERT INTO infra_capacidade_dia (dia, area, bytes, linhas) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (dia, area) DO UPDATE SET bytes = EXCLUDED.bytes, linhas = EXCLUDED.linhas",
                Date.valueOf(hoje), area, m.bytes(), m.linhas()));
        jdbcTemplate.update("DELETE FROM infra_capacidade_dia WHERE dia < ?", Date.valueOf(hoje.minusDays(DIAS_DE_HISTORICO)));
    }

    /** Avisa os administradores se o espaço estiver acabando. */
    public void conferirDisco() {
        Previsao.Disco d = disco();
        int nivel = Previsao.nivelDeAviso(d.percentual());
        if (nivel > 0) {
            avisoInfra.avisar("disco-" + nivel, "O espaço do CO-TI está em " + d.percentual() + "%. Restam "
                    + d.livreBytes() / GB + " GB. Veja o que mais cresce em Infraestrutura > Visão geral.");
        }
    }

    private Previsao.Disco disco() {
        File base = new File(ARQUIVOS).exists() ? new File(ARQUIVOS) : new File(".");
        Long banco = jdbcTemplate.queryForObject("SELECT pg_database_size(current_database())", Long.class);
        Map<String, Medida> hoje = medidasDe(LocalDate.now());
        long usado = (banco == null ? 0 : banco) + hoje.getOrDefault("arquivos", new Medida(0, 0)).bytes()
                + hoje.getOrDefault("logs-arquivo", new Medida(0, 0)).bytes();
        return Previsao.disco(base.getTotalSpace(), base.getUsableSpace(), usado, retencao.valor(RetencaoService.DISCO_LIMITE_GB) * GB);
    }

    private String guarda(String area) {
        return switch (area) {
            case "logs" -> {
                int limite = retencao.valor(RetencaoService.LOGS_LIMITE_GB);
                yield retencao.valor(RetencaoService.LOGS_MENSAGENS) + " dias" + (limite > 0 ? ", até " + limite + " GB" : "");
            }
            case "monitoramento" -> retencao.valor(RetencaoService.MONITOR_BATIDAS) + " dias; totais por hora, "
                    + retencao.valor(RetencaoService.MONITOR_HORAS) + " dias";
            case "sino" -> "Lidos, " + dias(RetencaoService.SINO_LIDAS) + "; não lidos, " + dias(RetencaoService.SINO_NAO_LIDAS);
            case "registros" -> "Conforme cada registro";
            case "logs-arquivo" -> "30 dias, até 1 GB";
            default -> "Sem limpeza automática";
        };
    }

    private String dias(String chave) {
        int v = retencao.valor(chave);
        return v == 0 ? "sempre" : v + " dias";
    }

    private void somarHypertable(Map<String, Medida> medidas, String area, String tabela) {
        // nomes fixos desta classe; approximate_row_count não varre a tabela
        List<Medida> r = jdbcTemplate.query("SELECT coalesce(hypertable_size('" + tabela + "'), 0) AS b, approximate_row_count('" + tabela + "') AS n",
                (rs, i) -> new Medida(rs.getLong("b"), rs.getLong("n")));
        Medida base = medidas.getOrDefault(area, new Medida(0, 0));
        medidas.put(area, new Medida(base.bytes() + r.get(0).bytes(), base.linhas() + r.get(0).linhas()));
    }

    private boolean temMedida(LocalDate dia) {
        Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM infra_capacidade_dia WHERE dia = ?", Integer.class, Date.valueOf(dia));
        return n != null && n > 0;
    }

    private Map<String, Medida> medidasDe(LocalDate dia) {
        Map<String, Medida> m = new HashMap<>();
        jdbcTemplate.query("SELECT area, bytes, linhas FROM infra_capacidade_dia WHERE dia = ?", rs -> {
            m.put(rs.getString("area"), new Medida(rs.getLong("bytes"), rs.getLong("linhas")));
        }, Date.valueOf(dia));
        return m;
    }

    private long tamanhoDaPasta(String nome) {
        Path pasta = Path.of(nome);
        if (!Files.isDirectory(pasta)) {
            return 0;
        }
        long[] total = {0};
        try {
            Files.walkFileTree(pasta, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    total[0] += a.size();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("[tamanhoDaPasta][{}]", e.getMessage());
        }
        return total[0];
    }

    private record Medida(long bytes, long linhas) {
    }

}
