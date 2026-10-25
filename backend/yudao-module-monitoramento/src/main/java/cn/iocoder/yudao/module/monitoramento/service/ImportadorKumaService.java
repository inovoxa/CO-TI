package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.monitoramento.config.MonitoramentoProperties;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.KumaVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.dominio.Estados;
import cn.iocoder.yudao.module.monitoramento.dominio.Regras;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.*;

/**
 * Importa o banco do Uptime Kuma (o arquivo {@code kuma.db}, SQLite) em duas etapas: analisar (o que há e o que entra)
 * e confirmar. Vêm os monitores dos tipos que o CO-TI verifica, os canais de aviso (Telegram, Matrix, webhook e
 * e-mail), as janelas de manutenção e, se pedido, o histórico recente. O que o Kuma não tem (unidade, papel no telão,
 * chamado automático) se preenche depois, monitor a monitor. O Kuma é só referência: o arquivo é lido, nunca alterado.
 */
@Service
@Slf4j
public class ImportadorKumaService {

    private static final long VALIDADE_MS = 60 * 60 * 1000L;
    private static final Map<String, String> TIPOS = Map.of("http", "http", "keyword", "palavra", "json-query", "json", "port", "tcp",
            "ping", "ping", "dns", "dns", "push", "push", "manual", "manual");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MonitorService monitorService;
    @Resource
    private CanalService canalService;
    @Resource
    private ManutencaoService manutencaoService;
    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private ExecucaoMonitores execucaoMonitores;
    @Resource
    private MonitoramentoProperties monitoramentoProperties;
    @Resource
    private DataSource dataSource;
    @Resource
    private AuditoriaService auditoriaService;

    @Value("${coti.monitoramento.kuma-pasta:arquivos/kuma}")
    private String pastaArquivos;

    private record Pendente(Path arquivo, long criadoEm) {
    }

    private final Map<String, Pendente> pendentes = new ConcurrentHashMap<>();

    // ================================================================================== 1. analisar

    public KumaVOs.Analise analisar(InputStream corpo, Long userId) throws IOException {
        limparExpirados();
        Path pasta = Path.of(pastaArquivos);
        Files.createDirectories(pasta);
        String token = UUID.randomUUID().toString().replace("-", "");
        Path arquivo = pasta.resolve(token + ".db");
        long max = monitoramentoProperties.getKumaMaxMb() * 1024L * 1024L;
        long total = 0;
        byte[] buf = new byte[1 << 16];
        try (OutputStream out = Files.newOutputStream(arquivo, StandardOpenOption.CREATE_NEW)) {
            int n;
            while ((n = corpo.read(buf)) > 0) {
                total += n;
                if (total > max) {
                    throw exception(KUMA_INVALIDO, "maior que " + monitoramentoProperties.getKumaMaxMb() + " MB");
                }
                out.write(buf, 0, n);
            }
        } catch (RuntimeException | IOException e) {
            Files.deleteIfExists(arquivo);
            throw e;
        }
        byte[] cabecalho = new byte[16];
        try (InputStream in = Files.newInputStream(arquivo)) {
            if (in.read(cabecalho) != 16 || !new String(cabecalho, 0, 15, StandardCharsets.US_ASCII).equals("SQLite format 3")) {
                Files.deleteIfExists(arquivo);
                throw exception(KUMA_INVALIDO, "não é um banco SQLite (envie o kuma.db da pasta data do Kuma)");
            }
        }
        KumaVOs.Analise a;
        try (Connection c = abrir(arquivo)) {
            if (!tabelas(c).containsAll(Set.of("monitor", "heartbeat", "notification"))) {
                throw exception(KUMA_INVALIDO, "não tem as tabelas do Uptime Kuma (monitor, heartbeat, notification)");
            }
            a = analisar(c);
        } catch (SQLException e) {
            Files.deleteIfExists(arquivo);
            throw exception(KUMA_INVALIDO, e.getMessage());
        } catch (RuntimeException e) {
            Files.deleteIfExists(arquivo);
            throw e;
        }
        a.setToken(token);
        pendentes.put(token, new Pendente(arquivo, System.currentTimeMillis()));
        log.info("[monitoramento] kuma.db recebido ({} MB) por {}: {} monitores, {} canais", total / (1024 * 1024), userId,
                a.getMonitores().size(), a.getCanais().size());
        return a;
    }

    private KumaVOs.Analise analisar(Connection c) throws SQLException {
        KumaVOs.Analise a = new KumaVOs.Analise();
        Set<String> jaImportados = importados();
        Map<Long, String> grupos = new HashMap<>();
        for (Map<String, Object> m : linhas(c, "SELECT * FROM monitor ORDER BY id")) {
            if ("group".equals(texto(m, "type"))) {
                grupos.put(longo(m, "id"), texto(m, "name"));
            }
        }
        for (Map<String, Object> m : linhas(c, "SELECT * FROM monitor ORDER BY id")) {
            KumaVOs.Item i = new KumaVOs.Item();
            i.setKumaId(longo(m, "id"));
            i.setNome(texto(m, "name"));
            String tipoKuma = texto(m, "type");
            i.setTipoKuma(tipoKuma);
            i.setTipo(TIPOS.get(tipoKuma));
            i.setAlvo(alvo(m));
            i.setAtivo(booleano(m, "active"));
            i.setJaImportado(jaImportados.contains(chaveKuma(i.getKumaId(), i.getNome())));
            if ("group".equals(tipoKuma)) {
                i.setAviso("grupo do Kuma: não vira monitor (o nome do grupo vai na descrição dos monitores dele)");
            } else if (i.getTipo() == null) {
                i.setAviso("tipo " + tipoKuma + " ainda não é verificado pelo CO-TI: não será importado");
            } else if ("json".equals(i.getTipo()) && !Regras.caminhoValido(texto(m, "json_path"))) {
                i.setAviso("a consulta JSON é uma expressão JSONata que o CO-TI não entende: entra pausado para revisar");
            } else if ("basic".equals(texto(m, "auth_method")) && texto(m, "basic_auth_user") != null) {
                i.setAviso("usa autenticação Basic: a credencial vai para os cabeçalhos do monitor");
            } else if (texto(m, "auth_method") != null && !Set.of("basic", "").contains(texto(m, "auth_method"))) {
                i.setAviso("autenticação " + texto(m, "auth_method") + " não é suportada: configure os cabeçalhos depois");
            }
            if (i.getAviso() == null && i.getJaImportado()) {
                i.setAviso("já importado antes: será ignorado");
            }
            a.getMonitores().add(i);
        }
        for (Map<String, Object> n : linhas(c, "SELECT * FROM notification ORDER BY id")) {
            Map<String, Object> cfg = json(texto(n, "config"));
            KumaVOs.Item i = new KumaVOs.Item();
            i.setKumaId(longo(n, "id"));
            i.setNome(texto(n, "name"));
            String tipoKuma = String.valueOf(cfg.getOrDefault("type", ""));
            i.setTipoKuma(tipoKuma);
            i.setTipo(switch (tipoKuma) {
                case "telegram" -> "telegram";
                case "matrix" -> "matrix";
                case "webhook" -> "webhook";
                case "smtp" -> "email";
                default -> null;
            });
            i.setAtivo(booleano(n, "active"));
            if (i.getTipo() == null) {
                i.setAviso("canal " + tipoKuma + " ainda não existe no CO-TI: não será importado");
            } else if ("email".equals(i.getTipo())) {
                i.setAviso("os e-mails saem pelo servidor SMTP configurado no CO-TI, não pelo do Kuma");
            } else if ("webhook".equals(i.getTipo()) && "custom".equals(cfg.get("webhookContentType"))) {
                i.setAviso("o corpo personalizado do webhook não é importado: o CO-TI manda o JSON dele");
            }
            a.getCanais().add(i);
        }
        if (tabelas(c).contains("maintenance")) {
            for (Map<String, Object> mt : linhas(c, "SELECT * FROM maintenance ORDER BY id")) {
                KumaVOs.Item i = new KumaVOs.Item();
                i.setKumaId(longo(mt, "id"));
                i.setNome(texto(mt, "title"));
                String estrategia = texto(mt, "strategy");
                i.setTipoKuma(estrategia);
                i.setTipo(switch (Optional.ofNullable(estrategia).orElse("")) {
                    case "manual" -> "manual";
                    case "single" -> "unica";
                    case "recurring-weekday" -> "semanal";
                    default -> null;
                });
                i.setAtivo(booleano(mt, "active"));
                if (i.getTipo() == null) {
                    i.setAviso("estratégia " + estrategia + " não existe no CO-TI (só manual, única e semanal): não será importada");
                }
                a.getManutencoes().add(i);
            }
        }
        String desde = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).format(DATA);
        try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM heartbeat WHERE time >= ?")) {
            ps.setString(1, desde);
            try (ResultSet rs = ps.executeQuery()) {
                a.setBatidas30d(rs.next() ? rs.getLong(1) : 0);
            }
        }
        if (tabelas(c).contains("tag")) {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM tag")) {
                a.setEtiquetas(rs.next() ? rs.getInt(1) : 0);
            }
        }
        return a;
    }

    // ================================================================================== 2. confirmar

    public KumaVOs.Resultado confirmar(KumaVOs.ConfirmarReq req, Long userId) {
        Pendente p = pendentes.remove(req.getToken());
        if (p == null || System.currentTimeMillis() - p.criadoEm() > VALIDADE_MS || !Files.exists(p.arquivo())) {
            throw exception(KUMA_ANALISE_EXPIRADA);
        }
        KumaVOs.Resultado res = new KumaVOs.Resultado();
        Set<Long> ignorar = new HashSet<>(Optional.ofNullable(req.getIgnorar()).orElse(List.of()));
        try (Connection c = abrir(p.arquivo())) {
            Set<String> tabelas = tabelas(c);
            // canais primeiro (os monitores se ligam a eles)
            Map<Long, Long> canais = new HashMap<>();
            if (!Boolean.FALSE.equals(req.getCanais())) {
                for (Map<String, Object> n : linhas(c, "SELECT * FROM notification ORDER BY id")) {
                    Long id = importarCanal(n, res);
                    if (id != null) {
                        canais.put(longo(n, "id"), id);
                    }
                }
            }
            Map<Long, List<Long>> canaisDoMonitor = new HashMap<>();
            if (tabelas.contains("monitor_notification")) {
                for (Map<String, Object> mn : linhas(c, "SELECT monitor_id, notification_id FROM monitor_notification")) {
                    Long canal = canais.get(longo(mn, "notification_id"));
                    if (canal != null) {
                        canaisDoMonitor.computeIfAbsent(longo(mn, "monitor_id"), k -> new ArrayList<>()).add(canal);
                    }
                }
            }
            Set<String> jaImportados = importados();
            Map<Long, String> grupos = new HashMap<>();
            List<Map<String, Object>> monitores = linhas(c, "SELECT * FROM monitor ORDER BY id");
            monitores.stream().filter(m -> "group".equals(texto(m, "type"))).forEach(m -> grupos.put(longo(m, "id"), texto(m, "name")));
            Map<Long, Long> novos = new LinkedHashMap<>();
            for (Map<String, Object> m : monitores) {
                Long kumaId = longo(m, "id");
                String tipo = TIPOS.get(texto(m, "type"));
                if (tipo == null || ignorar.contains(kumaId) || jaImportados.contains(chaveKuma(kumaId, texto(m, "name")))) {
                    continue;
                }
                try {
                    MonitorVOs.Monitor vo = monitor(m, tipo, grupos, res);
                    vo.setUnidadeId(req.getUnidadeId());
                    vo.setCanalIds(canaisDoMonitor.getOrDefault(kumaId, List.of()));
                    Long id = monitorService.salvar(vo);
                    novos.put(kumaId, id);
                    res.setMonitores(res.getMonitores() + 1);
                } catch (Exception e) {
                    res.getAvisos().add("monitor \"" + texto(m, "name") + "\" não importado: " + e.getMessage());
                }
            }
            if (!Boolean.FALSE.equals(req.getManutencoes()) && tabelas.contains("maintenance")) {
                Map<Long, List<Long>> monitoresDaManutencao = new HashMap<>();
                if (tabelas.contains("monitor_maintenance")) {
                    for (Map<String, Object> mm : linhas(c, "SELECT monitor_id, maintenance_id FROM monitor_maintenance")) {
                        Long novo = novos.get(longo(mm, "monitor_id"));
                        if (novo != null) {
                            monitoresDaManutencao.computeIfAbsent(longo(mm, "maintenance_id"), k -> new ArrayList<>()).add(novo);
                        }
                    }
                }
                for (Map<String, Object> mt : linhas(c, "SELECT * FROM maintenance ORDER BY id")) {
                    importarManutencao(mt, monitoresDaManutencao.getOrDefault(longo(mt, "id"), List.of()), res);
                }
            }
            int dias = Optional.ofNullable(req.getHistoricoDias()).orElse(0);
            if (!novos.isEmpty()) {
                res.setBatidas(importarHistorico(c, novos, dias));
            }
        } catch (SQLException e) {
            throw exception(KUMA_INVALIDO, e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(p.arquivo());
            } catch (IOException ignorado) {
                // fica para a limpeza
            }
        }
        execucaoMonitores.recarregar();
        auditoriaService.registrar("monitoramento.importar_kuma", userId, null, Map.of("monitores", res.getMonitores(),
                "canais", res.getCanais(), "manutencoes", res.getManutencoes(), "batidas", res.getBatidas()));
        return res;
    }

    private MonitorVOs.Monitor monitor(Map<String, Object> m, String tipo, Map<Long, String> grupos, KumaVOs.Resultado res) {
        MonitorVOs.Monitor vo = new MonitorVOs.Monitor();
        vo.setKumaId(longo(m, "id"));
        vo.setNome(cortar(Optional.ofNullable(texto(m, "name")).orElse("Monitor " + longo(m, "id")), 150));
        String descricao = texto(m, "description");
        Long pai = longo(m, "parent");
        if (pai != null && grupos.containsKey(pai)) {
            descricao = (descricao == null ? "" : descricao + "\n") + "Grupo no Uptime Kuma: " + grupos.get(pai);
        }
        vo.setDescricao(descricao);
        vo.setTipo(tipo);
        int intervalo = Math.max(5, Optional.ofNullable(inteiro(m, "interval")).orElse(60));
        vo.setIntervaloS(Math.min(intervalo, 86400));
        Integer tentativa = inteiro(m, "retry_interval");
        vo.setIntervaloTentativaS(tentativa == null || tentativa < 5 ? vo.getIntervaloS() : Math.min(tentativa, 86400));
        vo.setTentativas(Math.min(20, Math.max(0, Optional.ofNullable(inteiro(m, "maxretries")).orElse(0))));
        vo.setReenviarACada(Math.max(0, Optional.ofNullable(inteiro(m, "resend_interval")).orElse(0)));
        Double timeout = decimal(m, "timeout");
        vo.setTempoLimiteS(timeout == null || timeout <= 0 ? Math.max(1, Math.min(30, (int) (intervalo * 0.8)))
                : (int) Math.max(1, Math.min(300, Math.round(timeout))));
        vo.setInvertido(booleano(m, "upside_down"));
        vo.setIgnorarTls(booleano(m, "ignore_tls"));
        vo.setMaxRedirecionamentos(Math.min(30, Math.max(0, Optional.ofNullable(inteiro(m, "maxredirects")).orElse(10))));
        vo.setCertAvisoDias(m.containsKey("expiry_notification") && !booleano(m, "expiry_notification") ? 0 : 14);
        vo.setAtivo(booleano(m, "active"));
        switch (tipo) {
            case "http", "palavra", "json" -> {
                vo.setAlvo(texto(m, "url"));
                vo.setMetodo(Optional.ofNullable(texto(m, "method")).orElse("GET"));
                vo.setCorpo(texto(m, "body"));
                vo.setCodigosAceitos(codigos(texto(m, "accepted_statuscodes_json")));
                Map<String, Object> cab = json(texto(m, "headers"));
                if ("basic".equals(texto(m, "auth_method")) && texto(m, "basic_auth_user") != null) {
                    String par = texto(m, "basic_auth_user") + ":" + Optional.ofNullable(texto(m, "basic_auth_pass")).orElse("");
                    cab.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(par.getBytes(StandardCharsets.UTF_8)));
                }
                vo.setCabecalhos(cab.isEmpty() ? null : JsonUtils.toJsonString(cab));
                if ("palavra".equals(tipo)) {
                    vo.setPalavra(texto(m, "keyword"));
                    vo.setPalavraInvertida(booleano(m, "invert_keyword"));
                }
                if ("json".equals(tipo)) {
                    String caminho = texto(m, "json_path");
                    String op = Optional.ofNullable(texto(m, "json_path_operator")).orElse("==");
                    vo.setJsonOperador("contains".equals(op) ? "contem" : op);
                    vo.setJsonEsperado(texto(m, "expected_value"));
                    if (Regras.caminhoValido(caminho)) {
                        vo.setJsonCaminho(caminho);
                    } else {
                        vo.setJsonCaminho("");
                        vo.setAtivo(false);
                        vo.setDescricao((vo.getDescricao() == null ? "" : vo.getDescricao() + "\n") + "Consulta JSONata do Kuma (revisar): " + caminho);
                        res.getAvisos().add("\"" + vo.getNome() + "\" entrou pausado: a consulta JSON precisa ser revista");
                    }
                }
            }
            case "tcp" -> {
                vo.setAlvo(texto(m, "hostname"));
                vo.setPorta(inteiro(m, "port"));
            }
            case "ping" -> vo.setAlvo(texto(m, "hostname"));
            case "dns" -> {
                vo.setAlvo(texto(m, "hostname"));
                vo.setDnsTipo(Optional.ofNullable(texto(m, "dns_resolve_type")).orElse("A"));
                String servidor = texto(m, "dns_resolve_server");
                Integer porta = inteiro(m, "port");
                vo.setDnsServidor(servidor == null ? null : servidor + (porta != null && porta != 53 ? ":" + porta : ""));
            }
            case "push" -> {
                // o mesmo token: os scripts que chamam /api/push/<token> continuam funcionando
                vo.setPushToken(texto(m, "push_token"));
            }
            default -> {
            }
        }
        return vo;
    }

    private Long importarCanal(Map<String, Object> n, KumaVOs.Resultado res) {
        Map<String, Object> cfg = json(texto(n, "config"));
        String tipoKuma = String.valueOf(cfg.getOrDefault("type", ""));
        MonitorVOs.Canal c = new MonitorVOs.Canal();
        c.setNome(cortar(Optional.ofNullable(texto(n, "name")).orElse("Canal " + longo(n, "id")), 100));
        c.setPadrao(booleano(n, "is_default"));
        c.setAtivo(!n.containsKey("active") || booleano(n, "active"));
        Map<String, Object> config = new LinkedHashMap<>();
        switch (tipoKuma) {
            case "telegram" -> {
                c.setTipo("telegram");
                config.put("chatId", cfg.get("telegramChatID"));
                config.put("token", cfg.get("telegramBotToken"));
            }
            case "matrix" -> {
                c.setTipo("matrix");
                config.put("homeserver", cfg.get("homeserverUrl"));
                config.put("sala", cfg.get("internalRoomId"));
                config.put("token", cfg.get("accessToken"));
            }
            case "webhook" -> {
                c.setTipo("webhook");
                config.put("url", cfg.get("webhookURL"));
            }
            case "smtp" -> {
                c.setTipo("email");
                List<String> enderecos = new ArrayList<>();
                for (String campo : List.of("smtpTo", "smtpCC", "smtpBCC")) {
                    Object v = cfg.get(campo);
                    if (v != null) {
                        Arrays.stream(String.valueOf(v).split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).forEach(enderecos::add);
                    }
                }
                config.put("enderecos", enderecos);
            }
            default -> {
                return null;
            }
        }
        c.setConfig(config);
        try {
            Long id = canalService.salvar(c);
            res.setCanais(res.getCanais() + 1);
            return id;
        } catch (Exception e) {
            res.getAvisos().add("canal \"" + c.getNome() + "\" não importado: " + e.getMessage());
            return null;
        }
    }

    private void importarManutencao(Map<String, Object> mt, List<Long> monitores, KumaVOs.Resultado res) {
        String estrategia = Optional.ofNullable(texto(mt, "strategy")).orElse("");
        String tipo = switch (estrategia) {
            case "manual" -> "manual";
            case "single" -> "unica";
            case "recurring-weekday" -> "semanal";
            default -> null;
        };
        if (tipo == null) {
            return;
        }
        String titulo = Optional.ofNullable(texto(mt, "title")).orElse("Manutenção " + longo(mt, "id"));
        if (monitores.isEmpty()) {
            res.getAvisos().add("manutenção \"" + titulo + "\" não importada: nenhum dos monitores dela foi importado");
            return;
        }
        MonitorVOs.Manutencao vo = new MonitorVOs.Manutencao();
        vo.setTitulo(cortar(titulo, 150));
        vo.setDescricao(texto(mt, "description"));
        vo.setTipo(tipo);
        vo.setAtiva("manual".equals(tipo) ? booleano(mt, "active") && (!mt.containsKey("manual_active") || booleano(mt, "manual_active"))
                : booleano(mt, "active"));
        vo.setMonitorIds(monitores);
        try {
            if ("unica".equals(tipo)) {
                vo.setInicio(dataLocal(texto(mt, "start_date")));
                vo.setFim(dataLocal(texto(mt, "end_date")));
            } else if ("semanal".equals(tipo)) {
                List<Integer> dias = new ArrayList<>();
                for (Object d : JsonUtils.parseObject(Optional.ofNullable(texto(mt, "weekdays")).orElse("[]"), new TypeReference<List<Object>>() {
                })) {
                    int n = Integer.parseInt(String.valueOf(d));
                    dias.add(n == 0 ? 7 : n); // no Kuma, 0 é domingo
                }
                vo.setDiasSemana(dias);
                vo.setHoraInicio(hora(texto(mt, "start_time")));
                vo.setHoraFim(hora(texto(mt, "end_time")));
            }
            manutencaoService.salvar(vo);
            res.setManutencoes(res.getManutencoes() + 1);
            String fuso = texto(mt, "timezone");
            if (fuso != null && !Set.of("SAME_AS_SERVER", "").contains(fuso) && !fuso.equals(ZoneId.systemDefault().getId())) {
                res.getAvisos().add("manutenção \"" + titulo + "\" usava o fuso " + fuso + " no Kuma: confira os horários");
            }
        } catch (Exception e) {
            res.getAvisos().add("manutenção \"" + titulo + "\" não importada: " + e.getMessage());
        }
    }

    /** Batidas dos últimos dias (em lotes), os totais por hora e o estado atual de cada monitor importado. */
    private long importarHistorico(Connection c, Map<Long, Long> novos, int dias) throws SQLException {
        long total = 0;
        ZoneId zona = ZoneId.systemDefault();
        try (Connection pg = dataSource.getConnection()) {
            boolean auto = pg.getAutoCommit();
            pg.setAutoCommit(false);
            try (PreparedStatement ins = pg.prepareStatement("INSERT INTO monitor.batida (monitor_id, momento, estado, latencia_ms, msg, importante)"
                    + " VALUES (?, ?, ?, ?, ?, ?)");
                 PreparedStatement hora = pg.prepareStatement("INSERT INTO monitor.batida_hora (monitor_id, hora, no_ar, fora, manutencao, soma_latencia, n_latencia)"
                         + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (monitor_id, hora) DO UPDATE SET no_ar = batida_hora.no_ar + EXCLUDED.no_ar,"
                         + " fora = batida_hora.fora + EXCLUDED.fora, manutencao = batida_hora.manutencao + EXCLUDED.manutencao,"
                         + " soma_latencia = batida_hora.soma_latencia + EXCLUDED.soma_latencia, n_latencia = batida_hora.n_latencia + EXCLUDED.n_latencia")) {
                for (Map.Entry<Long, Long> e : novos.entrySet()) {
                    Map<LocalDateTime, long[]> horas = new TreeMap<>();
                    BatidaKuma ultima = null;
                    LocalDateTime ultimaMudanca = null;
                    if (dias > 0) {
                        try (PreparedStatement ps = c.prepareStatement("SELECT status, msg, time, ping, important FROM heartbeat"
                                + " WHERE monitor_id = ? AND time >= ? ORDER BY time")) {
                            ps.setLong(1, e.getKey());
                            ps.setString(2, LocalDateTime.now(ZoneOffset.UTC).minusDays(dias).format(DATA));
                            ps.setFetchSize(1000);
                            try (ResultSet rs = ps.executeQuery()) {
                                int lote = 0;
                                while (rs.next()) {
                                    BatidaKuma b = new BatidaKuma(rs.getInt(1), rs.getString(2), utcParaLocal(rs.getString(3), zona),
                                            rs.getObject(4) == null ? null : (int) Math.round(rs.getDouble(4)), rs.getBoolean(5));
                                    if (b.momento() == null || b.estado() < 0 || b.estado() > 3) {
                                        continue;
                                    }
                                    Integer lat = b.estado() == Estados.NO_AR ? b.latencia() : null;
                                    ins.setLong(1, e.getValue());
                                    ins.setTimestamp(2, Timestamp.valueOf(b.momento()));
                                    ins.setInt(3, b.estado());
                                    ins.setObject(4, lat);
                                    ins.setString(5, cortar(b.msg(), 500));
                                    ins.setBoolean(6, b.importante());
                                    ins.addBatch();
                                    long[] h = horas.computeIfAbsent(b.momento().withMinute(0).withSecond(0).withNano(0), k -> new long[5]);
                                    if (b.estado() == Estados.NO_AR) {
                                        h[0]++;
                                        if (lat != null) {
                                            h[3] += lat;
                                            h[4]++;
                                        }
                                    } else if (b.estado() == Estados.MANUTENCAO) {
                                        h[2]++;
                                    } else {
                                        h[1]++;
                                    }
                                    if (b.importante() || ultima == null || ultima.estado() != b.estado()) {
                                        ultimaMudanca = b.momento();
                                    }
                                    ultima = b;
                                    total++;
                                    if (++lote % 1000 == 0) {
                                        ins.executeBatch();
                                    }
                                }
                                ins.executeBatch();
                            }
                        }
                        for (Map.Entry<LocalDateTime, long[]> h : horas.entrySet()) {
                            long[] v = h.getValue();
                            hora.setLong(1, e.getValue());
                            hora.setTimestamp(2, Timestamp.valueOf(h.getKey()));
                            hora.setLong(3, v[0]);
                            hora.setLong(4, v[1]);
                            hora.setLong(5, v[2]);
                            hora.setLong(6, v[3]);
                            hora.setLong(7, v[4]);
                            hora.addBatch();
                        }
                        hora.executeBatch();
                    }
                    if (ultima == null) {
                        ultima = ultimaBatida(c, e.getKey(), zona);
                        ultimaMudanca = ultima == null ? null : ultima.momento();
                    }
                    if (ultima != null) {
                        // continua de onde o Kuma parou (sem aviso: não é mudança nova)
                        try (PreparedStatement u = pg.prepareStatement("UPDATE monitor.monitor SET estado = ?, estado_desde = ?, ultima_verificacao = ?,"
                                + " ultima_latencia_ms = ?, ultima_msg = ? WHERE id = ?")) {
                            u.setInt(1, ultima.estado());
                            u.setTimestamp(2, Timestamp.valueOf(ultimaMudanca));
                            u.setTimestamp(3, Timestamp.valueOf(ultima.momento()));
                            u.setObject(4, ultima.estado() == Estados.NO_AR ? ultima.latencia() : null);
                            u.setString(5, cortar(ultima.msg(), 500));
                            u.setLong(6, e.getValue());
                            u.executeUpdate();
                        }
                    }
                    pg.commit();
                }
            } catch (SQLException ex) {
                pg.rollback();
                throw ex;
            } finally {
                pg.setAutoCommit(auto);
            }
        }
        return total;
    }

    private record BatidaKuma(int estado, String msg, LocalDateTime momento, Integer latencia, boolean importante) {
    }

    private BatidaKuma ultimaBatida(Connection c, Long kumaId, ZoneId zona) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT status, msg, time, ping, important FROM heartbeat WHERE monitor_id = ? ORDER BY time DESC LIMIT 1")) {
            ps.setLong(1, kumaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                LocalDateTime t = utcParaLocal(rs.getString(3), zona);
                return t == null ? null : new BatidaKuma(rs.getInt(1), rs.getString(2), t,
                        rs.getObject(4) == null ? null : (int) Math.round(rs.getDouble(4)), rs.getBoolean(5));
            }
        }
    }

    // ================================================================================== apoio

    /**
     * Monitores já importados, por id no Kuma + nome: cada instalação do Kuma numera os monitores a partir de 1, então
     * só o id confundiria dois Kumas diferentes.
     */
    private Set<String> importados() {
        Set<String> out = new HashSet<>();
        monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().isNotNull(MonitorDO::getKumaId))
                .forEach(m -> out.add(chaveKuma(m.getKumaId(), m.getNome())));
        return out;
    }

    private static String chaveKuma(Long kumaId, String nome) {
        return kumaId + "|" + cortar(Optional.ofNullable(nome).orElse("Monitor " + kumaId), 150).trim().toLowerCase(Locale.ROOT);
    }

    private Connection abrir(Path arquivo) throws SQLException {
        Properties p = new Properties();
        p.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
        p.setProperty("enable_load_extension", "false");
        return DriverManager.getConnection("jdbc:sqlite:" + arquivo.toAbsolutePath(), p);
    }

    private static Set<String> tabelas(Connection c) throws SQLException {
        Set<String> out = new HashSet<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> linhas(Connection c, String sql) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    m.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                }
                out.add(m);
            }
        }
        return out;
    }

    private static String alvo(Map<String, Object> m) {
        String url = texto(m, "url");
        if (url != null && !url.equals("https://")) {
            return url;
        }
        String host = texto(m, "hostname");
        Integer porta = inteiro(m, "port");
        return host == null ? null : host + ("port".equals(texto(m, "type")) && porta != null ? ":" + porta : "");
    }

    /** Lista JSON do Kuma (["200-299","301"]) para "200-299,301". */
    private static String codigos(String json) {
        if (json == null || json.isBlank()) {
            return "200-299";
        }
        try {
            List<Object> l = JsonUtils.parseObject(json, new TypeReference<List<Object>>() {
            });
            String s = l == null ? "" : String.join(",", l.stream().map(String::valueOf).toList());
            return s.isBlank() || !Regras.codigosValidos(s) ? "200-299" : s;
        } catch (Exception e) {
            return "200-299";
        }
    }

    private static Map<String, Object> json(String s) {
        if (s == null || s.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> m = JsonUtils.parseObject(s, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return m == null ? new LinkedHashMap<>() : m;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** O Kuma grava as batidas em UTC ("2026-10-25 12:34:56.789"). */
    static LocalDateTime utcParaLocal(String s, ZoneId zona) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            String t = s.trim().replace('T', ' ').replace("Z", "");
            if (t.length() > 19) {
                t = t.substring(0, 19);
            }
            return LocalDateTime.parse(t, DATA).atOffset(ZoneOffset.UTC).atZoneSameInstant(zona).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime dataLocal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim().replace('T', ' ');
        if (t.length() == 16) {
            t += ":00";
        }
        return LocalDateTime.parse(t.substring(0, 19), DATA);
    }

    private static LocalTime hora(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return LocalTime.parse(t.length() == 5 ? t : t.substring(0, 5));
    }

    private void limparExpirados() {
        long agora = System.currentTimeMillis();
        pendentes.entrySet().removeIf(e -> {
            if (agora - e.getValue().criadoEm() > VALIDADE_MS) {
                try {
                    Files.deleteIfExists(e.getValue().arquivo());
                } catch (IOException ignorado) {
                    // tenta de novo na próxima
                }
                return true;
            }
            return false;
        });
    }

    private static String texto(Map<String, Object> m, String c) {
        Object v = m.get(c);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }

    private static Long longo(Map<String, Object> m, String c) {
        Object v = m.get(c);
        if (v == null) {
            return null;
        }
        try {
            return v instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer inteiro(Map<String, Object> m, String c) {
        Long l = longo(m, c);
        return l == null ? null : (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, l));
    }

    private static Double decimal(Map<String, Object> m, String c) {
        Object v = m.get(c);
        if (v == null) {
            return null;
        }
        try {
            return v instanceof Number n ? n.doubleValue() : Double.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean booleano(Map<String, Object> m, String c) {
        Object v = m.get(c);
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim();
        return s.equals("1") || s.equalsIgnoreCase("true");
    }

    private static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
