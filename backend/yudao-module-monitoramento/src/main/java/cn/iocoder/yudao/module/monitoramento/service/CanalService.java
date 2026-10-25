package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.itsm.service.email.NotificacaoEmailService;
import cn.iocoder.yudao.module.monitoramento.config.MonitoramentoProperties;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.CanalDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorCanalDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonCanalMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorCanalMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.*;

/**
 * Canais de aviso dos monitores e o envio. Um canal que falha não impede os outros; a falha vai para o log.
 */
@Service
@Slf4j
public class CanalService {

    static final String CONTEXTO = "monitoramento-canal";
    /** Campo secreto de cada tipo (guardado cifrado, devolvido mascarado) */
    private static final Map<String, String> SEGREDO = Map.of("telegram", "token", "matrix", "token", "webhook", "segredo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Resource
    private MonCanalMapper monCanalMapper;
    @Resource
    private MonMonitorCanalMapper monMonitorCanalMapper;
    @Resource
    private CifradorSegredos cifradorSegredos;
    @Resource
    private NotificacaoEmailService notificacaoEmailService;
    @Resource
    private MonitoramentoProperties monitoramentoProperties;

    @Value("${monitor.matrix.homeserver:}")
    private String matrixServidor;
    @Value("${monitor.matrix.token:}")
    private String matrixToken;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** O que aconteceu, para montar a mensagem de cada canal. */
    public record Aviso(String evento, MonitorDO monitor, String unidade, String msg, Long duracaoS, String link) {
        /** caiu | voltou | reaviso | certificado | teste */
        public String titulo() {
            String n = monitor.getNome();
            return switch (evento) {
                case "caiu" -> "🔴 Fora do ar: " + n;
                case "reaviso" -> "🔴 Continua fora do ar: " + n;
                case "voltou" -> "🟢 Normalizado: " + n;
                case "certificado" -> "🟠 Certificado perto de vencer: " + n;
                default -> "ℹ️ Teste do canal de aviso do CO-TI";
            };
        }

        public String texto() {
            StringBuilder s = new StringBuilder(titulo());
            if (!"teste".equals(evento)) {
                s.append("\n").append(Optional.ofNullable(monitor.getAlvo()).orElse(monitor.getTipo()));
                if (unidade != null) {
                    s.append(" · ").append(unidade);
                }
                if (msg != null && !msg.isBlank()) {
                    s.append("\n").append(msg);
                }
                if ("voltou".equals(evento) && duracaoS != null) {
                    s.append("\nFicou fora por ").append(MonitorService.duracao(duracaoS));
                }
            } else {
                s.append("\nSe você recebeu esta mensagem, o canal está funcionando.");
            }
            return s.toString();
        }
    }

    // ================================================================================== cadastro

    public List<MonitorVOs.Canal> listar() {
        Map<Long, Long> uso = monMonitorCanalMapper.selectList().stream()
                .collect(Collectors.groupingBy(MonitorCanalDO::getCanalId, Collectors.counting()));
        return monCanalMapper.selectList(new LambdaQueryWrapperX<CanalDO>().orderByAsc(CanalDO::getNome)).stream().map(c -> {
            MonitorVOs.Canal vo = new MonitorVOs.Canal();
            vo.setId(c.getId());
            vo.setNome(c.getNome());
            vo.setTipo(c.getTipo());
            vo.setConfig(mascarar(c.getTipo(), ler(c.getConfig())));
            vo.setPadrao(c.getPadrao());
            vo.setAtivo(c.getAtivo());
            vo.setMonitores(uso.getOrDefault(c.getId(), 0L).intValue());
            return vo;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(MonitorVOs.Canal req) {
        if (!Set.of("email", "matrix", "telegram", "webhook").contains(req.getTipo())) {
            throw exception(CANAL_INVALIDO, "tipo deve ser email, matrix, telegram ou webhook");
        }
        Map<String, Object> config = new LinkedHashMap<>(Optional.ofNullable(req.getConfig()).orElse(Map.of()));
        config.remove(SEGREDO.getOrDefault(req.getTipo(), "") + "Definido");
        CanalDO atual = req.getId() == null ? null : monCanalMapper.selectById(req.getId());
        if (req.getId() != null && atual == null) {
            throw exception(CANAL_NAO_EXISTE);
        }
        String campo = SEGREDO.get(req.getTipo());
        if (campo != null) {
            Object novo = config.get(campo);
            if (novo == null || String.valueOf(novo).isBlank()) {
                // manteve o segredo guardado (a tela não o recebe de volta)
                Object antigo = atual == null || !atual.getTipo().equals(req.getTipo()) ? null : ler(atual.getConfig()).get(campo);
                if (antigo == null) {
                    config.remove(campo);
                } else {
                    config.put(campo, antigo);
                }
            } else {
                if (!cifradorSegredos.configurado()) {
                    throw exception(CANAL_SEM_CHAVE_MESTRA);
                }
                config.put(campo, cifradorSegredos.cifrar(String.valueOf(novo).trim(), CONTEXTO));
            }
        }
        validar(req.getTipo(), config);
        CanalDO c = CanalDO.builder().id(req.getId()).nome(req.getNome().trim()).tipo(req.getTipo()).config(JsonUtils.toJsonString(config))
                .padrao(Boolean.TRUE.equals(req.getPadrao())).ativo(req.getAtivo() == null || req.getAtivo()).build();
        if (c.getId() == null) {
            monCanalMapper.insert(c);
        } else {
            monCanalMapper.updateById(c);
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        monCanalMapper.deleteById(id);
        monMonitorCanalMapper.delete(MonitorCanalDO::getCanalId, id);
    }

    /** Manda uma mensagem de teste agora. @return null se deu certo; senão, o erro */
    public String testar(Long id) {
        CanalDO c = monCanalMapper.selectById(id);
        if (c == null) {
            throw exception(CANAL_NAO_EXISTE);
        }
        MonitorDO falso = MonitorDO.builder().id(0L).nome("Teste").tipo("http").alvo("—").build();
        try {
            enviar(c, new Aviso("teste", falso, null, null, null, "/monitoramento/canais"));
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    public List<Long> padroes() {
        return monCanalMapper.selectList(new LambdaQueryWrapperX<CanalDO>().eq(CanalDO::getPadrao, true)).stream().map(CanalDO::getId).toList();
    }

    // ================================================================================== envio

    /** Envia o aviso por todos os canais ativos ligados ao monitor. */
    @DataPermission(enable = false)
    public int avisar(Aviso aviso) {
        List<Long> ids = monMonitorCanalMapper.selectList(MonitorCanalDO::getMonitorId, aviso.monitor().getId()).stream()
                .map(MonitorCanalDO::getCanalId).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        int enviados = 0;
        for (CanalDO c : monCanalMapper.selectByIds(ids)) {
            if (!Boolean.TRUE.equals(c.getAtivo())) {
                continue;
            }
            try {
                enviar(c, aviso);
                enviados++;
            } catch (Exception e) {
                log.warn("[monitoramento] aviso '{}' do monitor {} pelo canal {} ({}) falhou: {}", aviso.evento(), aviso.monitor().getId(),
                        c.getNome(), c.getTipo(), e.getMessage());
            }
        }
        return enviados;
    }

    void enviar(CanalDO c, Aviso a) throws Exception {
        Map<String, Object> cfg = ler(c.getConfig());
        switch (c.getTipo()) {
            case "email" -> email(cfg, a);
            case "telegram" -> telegram(cfg, a);
            case "matrix" -> matrix(cfg, a);
            case "webhook" -> webhook(cfg, a);
            default -> throw new IllegalStateException("tipo de canal desconhecido: " + c.getTipo());
        }
    }

    private void email(Map<String, Object> cfg, Aviso a) {
        List<Long> pessoas = lista(cfg.get("pessoas")).stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        List<String> enderecos = lista(cfg.get("enderecos")).stream().map(String::valueOf).toList();
        String evento = switch (a.evento()) {
            case "voltou" -> "monitor-voltou";
            case "certificado" -> "monitor-certificado";
            default -> "monitor-caiu";
        };
        MonitorDO m = a.monitor();
        Map<String, String> v = new HashMap<>();
        v.put("monitor.nome", m.getNome());
        v.put("monitor.alvo", Optional.ofNullable(m.getAlvo()).orElse(m.getTipo()));
        v.put("monitor.unidade", Optional.ofNullable(a.unidade()).orElse(""));
        v.put("monitor.desde", LocalDateTime.now().format(DATA));
        v.put("monitor.msg", Optional.ofNullable(a.msg()).orElse(""));
        v.put("monitor.duracao", a.duracaoS() == null ? "" : MonitorService.duracao(a.duracaoS()));
        v.put("monitor.dias", m.getCertDias() == null ? "" : String.valueOf(m.getCertDias()));
        v.put("monitor.validade", m.getCertValidade() == null ? "" : m.getCertValidade().format(DATA));
        if ("teste".equals(a.evento())) {
            v.put("monitor.nome", "Teste do canal de aviso");
            v.put("monitor.msg", "Se você recebeu esta mensagem, o canal está funcionando.");
        }
        int n = notificacaoEmailService.avisar(evento, pessoas, enderecos, v, a.link());
        if (n == 0) {
            throw new IllegalStateException("nenhum e-mail na fila: confira o envio de e-mails (Service Desk → E-mail), o modelo do aviso e os destinatários");
        }
    }

    private void telegram(Map<String, Object> cfg, Aviso a) throws Exception {
        String token = segredo(cfg, "token");
        Object chat = cfg.get("chatId");
        if (token == null || chat == null) {
            throw new IllegalStateException("informe o token do bot e o chat");
        }
        String corpo = JsonUtils.toJsonString(Map.of("chat_id", String.valueOf(chat), "text", a.texto(), "disable_web_page_preview", true));
        HttpRequest req = HttpRequest.newBuilder(URI.create(monitoramentoProperties.getTelegramApi().replaceAll("/+$", "") + "/bot" + token + "/sendMessage"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
        exigirSucesso(http.send(req, HttpResponse.BodyHandlers.ofString()), "Telegram");
    }

    private void matrix(Map<String, Object> cfg, Aviso a) throws Exception {
        String servidor = Optional.ofNullable(cfg.get("homeserver")).map(String::valueOf).filter(s -> !s.isBlank()).orElse(matrixServidor);
        String token = Optional.ofNullable(segredo(cfg, "token")).orElse(matrixToken);
        String sala = cfg.get("sala") == null ? null : String.valueOf(cfg.get("sala")).trim();
        if (servidor == null || servidor.isBlank() || token == null || token.isBlank() || sala == null || sala.isBlank()) {
            throw new IllegalStateException("informe a sala (e o servidor e token, se não forem os do CO-TI)");
        }
        servidor = servidor.replaceAll("/+$", "");
        String sala2 = sala;
        if (sala.startsWith("#")) {
            HttpRequest r = HttpRequest.newBuilder(URI.create(servidor + "/_matrix/client/v3/directory/room/" + enc(sala)))
                    .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
            exigirSucesso(resp, "Matrix (sala " + sala + ")");
            sala2 = String.valueOf(JsonUtils.parseObject(resp.body(), Map.class).get("room_id"));
        }
        Map<String, Object> corpo = Map.of("msgtype", "caiu".equals(a.evento()) || "reaviso".equals(a.evento()) ? "m.text" : "m.notice", "body", a.texto());
        HttpRequest req = HttpRequest.newBuilder(URI.create(servidor + "/_matrix/client/v3/rooms/" + enc(sala2) + "/send/m.room.message/"
                        + UUID.randomUUID())).header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15)).PUT(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(corpo))).build();
        exigirSucesso(http.send(req, HttpResponse.BodyHandlers.ofString()), "Matrix");
    }

    private void webhook(Map<String, Object> cfg, Aviso a) throws Exception {
        String url = cfg.get("url") == null ? null : String.valueOf(cfg.get("url")).trim();
        if (url == null || !url.matches("(?i)https?://.+")) {
            throw new IllegalStateException("informe a URL (http ou https)");
        }
        MonitorDO m = a.monitor();
        Map<String, Object> monitor = new LinkedHashMap<>();
        monitor.put("id", m.getId());
        monitor.put("nome", m.getNome());
        monitor.put("tipo", m.getTipo());
        monitor.put("alvo", m.getAlvo());
        monitor.put("unidade", a.unidade());
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("evento", a.evento());
        corpo.put("monitor", monitor);
        corpo.put("estado", m.getEstado());
        corpo.put("msg", a.msg());
        corpo.put("latenciaMs", m.getUltimaLatenciaMs());
        corpo.put("duracaoForaS", a.duracaoS());
        corpo.put("chamadoId", m.getChamadoId());
        corpo.put("momento", LocalDateTime.now().toString());
        corpo.put("texto", a.texto());
        String json = JsonUtils.toJsonString(corpo);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("User-Agent", "CO-TI-Monitor/1.0");
        String segredo = segredo(cfg, "segredo");
        if (segredo != null) {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            b.header("X-COTI-Assinatura", "sha256=" + HexFormat.of().formatHex(mac.doFinal(json.getBytes(StandardCharsets.UTF_8))));
        }
        exigirSucesso(http.send(b.POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString()), "webhook");
    }

    // ================================================================================== apoio

    private static void validar(String tipo, Map<String, Object> cfg) {
        switch (tipo) {
            case "email" -> {
                if (lista(cfg.get("pessoas")).isEmpty() && lista(cfg.get("enderecos")).isEmpty()) {
                    throw exception(CANAL_INVALIDO, "escolha as pessoas ou informe os e-mails");
                }
            }
            case "telegram" -> {
                if (cfg.get("chatId") == null || String.valueOf(cfg.get("chatId")).isBlank() || cfg.get("token") == null) {
                    throw exception(CANAL_INVALIDO, "informe o token do bot e o chat");
                }
            }
            case "matrix" -> {
                if (cfg.get("sala") == null || String.valueOf(cfg.get("sala")).isBlank()) {
                    throw exception(CANAL_INVALIDO, "informe a sala (#sala:servidor ou !id:servidor)");
                }
            }
            case "webhook" -> {
                if (cfg.get("url") == null || !String.valueOf(cfg.get("url")).trim().matches("(?i)https?://.+")) {
                    throw exception(CANAL_INVALIDO, "informe a URL (http ou https)");
                }
            }
            default -> {
            }
        }
    }

    private String segredo(Map<String, Object> cfg, String campo) {
        Object v = cfg.get(campo);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return cifradorSegredos.decifrar(String.valueOf(v), CONTEXTO);
    }

    private static Map<String, Object> mascarar(String tipo, Map<String, Object> cfg) {
        Map<String, Object> out = new LinkedHashMap<>(cfg);
        String campo = SEGREDO.get(tipo);
        if (campo != null) {
            out.put(campo + "Definido", out.remove(campo) != null);
        }
        return out;
    }

    static Map<String, Object> ler(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> m = JsonUtils.parseObject(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        return m == null ? new LinkedHashMap<>() : m;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> lista(Object v) {
        if (v instanceof Collection<?> c) {
            return new ArrayList<>((Collection<Object>) c).stream().filter(x -> x != null && !String.valueOf(x).isBlank()).toList();
        }
        if (v instanceof String s && !s.isBlank()) {
            return Arrays.stream(s.split("[,;\\s]+")).filter(x -> !x.isBlank()).map(x -> (Object) x).toList();
        }
        return List.of();
    }

    private static void exigirSucesso(HttpResponse<String> r, String onde) {
        if (r.statusCode() >= 300) {
            String corpo = r.body() == null ? "" : r.body();
            throw new IllegalStateException(onde + " respondeu HTTP " + r.statusCode() + (corpo.isBlank() ? "" : ": " + (corpo.length() > 200 ? corpo.substring(0, 200) : corpo)));
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

}
