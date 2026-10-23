package cn.iocoder.yudao.module.system.service.monitor;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.OcorrenciaVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Posta as ocorrências do telão numa sala do Matrix (Synapse), como o "bot" do Centro de Operações.
 *
 * <p>Configuração no application.yaml:
 * <pre>
 * monitor:
 *   matrix:
 *     enabled: true
 *     homeserver: https://matrix.orgao.gov.br
 *     token: ${MATRIX_BOT_TOKEN}          # token do usuário @centro-operacoes (NÃO do administrador)
 *     sala-alertas: "#noc-alertas:orgao.gov.br"
 *     sala-seguranca: "#soc:orgao.gov.br"
 *     nivel-minimo: critico                 # info | atencao | critico (atencao gera bem mais mensagens)
 * </pre>
 *
 * <p>A sala #noc-alertas é criada sem criptografia de ponta a ponta pelo script de sincronização,
 * justamente para que este cliente simples possa postar. Para salas cifradas seria preciso um bot
 * com suporte a E2EE (por exemplo, com a biblioteca matrix-nio ou o Pantalaimon como proxy).
 */
@Slf4j
@Service
public class MatrixNotifier {

    @Value("${monitor.matrix.enabled:false}")
    private boolean enabled;
    @Value("${monitor.matrix.homeserver:}")
    private String homeserver;
    @Value("${monitor.matrix.token:}")
    private String token;
    @Value("${monitor.matrix.sala-alertas:}")
    private String salaAlertas;
    @Value("${monitor.matrix.sala-seguranca:}")
    private String salaSeguranca;
    @Value("${monitor.matrix.nivel-minimo:critico}")
    private String nivelMinimo;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    /** alias (#sala:servidor) -> room_id (!abc:servidor) */
    private final Map<String, String> roomIds = new ConcurrentHashMap<>();

    /** Envia as ocorrências novas. Erros só vão para o log: o telão nunca para por causa do chat. */
    public void enviar(List<OcorrenciaVO> ocorrencias, Map<Long, String> nomeUnidade) {
        if (!enabled || ocorrencias.isEmpty()) {
            return;
        }
        for (OcorrenciaVO o : ocorrencias) {
            if (peso(o.getNivel()) < peso(nivelMinimo)) {
                continue;
            }
            String unidade = nomeUnidade.getOrDefault(o.getUnitId(), "Unidade " + o.getUnitId());
            try {
                postar(salaAlertas, o, unidade);
                if ("seguranca".equals(o.getCategoria()) && !salaSeguranca.isEmpty()) {
                    postar(salaSeguranca, o, unidade);
                }
            } catch (Exception e) {
                log.warn("[matrix] falha ao postar ocorrência: {}", e.getMessage());
            }
        }
    }

    private void postar(String alias, OcorrenciaVO o, String unidade) throws Exception {
        String roomId = resolver(alias);
        String cor = "critico".equals(o.getNivel()) ? "#c81e1e" : "atencao".equals(o.getNivel()) ? "#b45309" : "#15803d";
        String rotulo = "critico".equals(o.getNivel()) ? "CRÍTICO" : "atencao".equals(o.getNivel()) ? "ATENÇÃO" : "INFO";

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("msgtype", "critico".equals(o.getNivel()) ? "m.text" : "m.notice"); // notice não gera notificação sonora
        corpo.put("body", String.format("[%s] %s · %s: %s", rotulo, unidade, o.getCategoria(), o.getTexto()));
        corpo.put("format", "org.matrix.custom.html");
        corpo.put("formatted_body", String.format("<font color=\"%s\"><b>%s</b></font> · <b>%s</b> · %s<br>%s",
                cor, rotulo, escapar(unidade), escapar(o.getCategoria()), escapar(o.getTexto())));

        // PUT com txnId único: se a mesma chamada for repetida, o Synapse não duplica a mensagem
        String txn = UUID.randomUUID().toString();
        HttpRequest req = HttpRequest.newBuilder(URI.create(homeserver + "/_matrix/client/v3/rooms/" + enc(roomId)
                        + "/send/m.room.message/" + txn))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .PUT(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(corpo)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " " + resp.body());
        }
    }

    private String resolver(String alias) throws Exception {
        if (alias.startsWith("!")) {
            return alias;
        }
        String cached = roomIds.get(alias);
        if (cached != null) {
            return cached;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(homeserver + "/_matrix/client/v3/directory/room/" + enc(alias)))
                .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("sala " + alias + " não encontrada (HTTP " + resp.statusCode() + ")");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> json = JsonUtils.parseObject(resp.body(), Map.class);
        String roomId = String.valueOf(json.get("room_id"));
        roomIds.put(alias, roomId);
        return roomId;
    }

    private static int peso(String nivel) {
        return "critico".equals(nivel) ? 2 : "atencao".equals(nivel) ? 1 : 0;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

}
