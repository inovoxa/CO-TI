package cn.iocoder.yudao.module.infraestrutura.service;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.IntegracaoVOs;
import cn.iocoder.yudao.module.infraestrutura.dominio.DestinoRegras;
import cn.iocoder.yudao.module.infraestrutura.dominio.EntradaRegras;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.service.ChamadoEvento;
import cn.iocoder.yudao.module.system.service.retencao.AvisoInfraService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Avisa outros sistemas do andamento dos chamados. O aviso entra numa fila e o envio acontece fora da transação do chamado: um
 * destino lento ou fora do ar não atrasa o atendimento. O conteúdo é mínimo (identificadores e situação): quem recebe consulta o
 * resto pela API, com as permissões dela.
 */
@Slf4j
@Service
public class WebhookSaidaService {

    /** Ação do chamado → evento publicado. Andamentos ficam de fora: podem ser privados. */
    private static final Map<String, String> EVENTOS = Map.of("criado", "chamado.criado", "ator", "chamado.atribuido",
            "solucionado", "chamado.solucionado", "fechado", "chamado.fechado", "solucao-aprovada", "chamado.fechado",
            "reaberto", "chamado.reaberto", "solucao-recusada", "chamado.reaberto");
    private static final Set<String> VALIDOS = Set.copyOf(EVENTOS.values());
    private static final int[] ESPERA_MIN = {1, 5, 15, 60};
    private static final ErrorCode INVALIDA = new ErrorCode(1_098_000_003, "{}");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Sem seguir redirecionamento: ele poderia levar a um destino que a validação recusaria. */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    private AvisoInfraService avisoInfra;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @DataPermission(enable = false)
    public void aoMudarChamado(ChamadoEvento e) {
        String evento = EVENTOS.get(e.acao());
        if (evento == null) {
            return;
        }
        try {
            List<Long> saidas = jdbcTemplate.queryForList("SELECT id FROM integracao.saida WHERE ativa AND (',' || eventos || ',') LIKE ?", Long.class, "%," + evento + ",%");
            if (saidas.isEmpty()) {
                return;
            }
            ChamadoDO c = chamadoMapper.selectById(e.chamadoId());
            if (c == null) {
                return;
            }
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("evento", evento);
            corpo.put("chamado", c.getId());
            corpo.put("status", c.getStatus());
            corpo.put("categoria", c.getCategoriaId());
            corpo.put("momento", LocalDateTime.now().toString());
            String payload = JsonUtils.toJsonString(corpo);
            for (Long s : saidas) {
                jdbcTemplate.update("INSERT INTO integracao.entrega (id, saida_id, evento, chamado_id, payload) VALUES (nextval('integracao.entrega_seq'), ?, ?, ?, ?)",
                        s, evento, c.getId(), payload);
            }
        } catch (RuntimeException ex) {
            // o aviso a outro sistema nunca atrapalha o chamado
            log.error("[aoMudarChamado][chamado {} evento {}: {}]", e.chamadoId(), evento, ex.getClass().getSimpleName());
        }
    }

    public int processarFila() {
        jdbcTemplate.update("UPDATE integracao.entrega SET status = 'pendente' WHERE status = 'enviando' AND proxima_tentativa < now() - interval '5 minutes'");
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM integracao.entrega WHERE status = 'pendente' AND (proxima_tentativa IS NULL OR proxima_tentativa <= now()) ORDER BY id LIMIT 50", Long.class);
        int n = 0;
        for (Long id : ids) {
            if (jdbcTemplate.update("UPDATE integracao.entrega SET status = 'enviando', tentativas = tentativas + 1, proxima_tentativa = now() WHERE id = ? AND status = 'pendente'", id) == 1) {
                enviar(id);
                n++;
            }
        }
        return n;
    }

    private void enviar(Long id) {
        Map<String, Object> e = jdbcTemplate.queryForMap("SELECT d.id, d.evento, d.payload, d.tentativas, d.saida_id, s.url, s.segredo_cifrado, s.ativa, s.nome "
                + "FROM integracao.entrega d JOIN integracao.saida s ON s.id = d.saida_id WHERE d.id = ?", id);
        int tentativas = ((Number) e.get("tentativas")).intValue();
        String url = (String) e.get("url");
        String nome = (String) e.get("nome");
        if (!Boolean.TRUE.equals(e.get("ativa"))) {
            terminar(id, "erro", null, "A saída foi desativada");
            return;
        }
        // confere de novo a cada envio: o nome pode ter passado a apontar para outro endereço depois do cadastro
        String recusa = DestinoRegras.motivoDeRecusa(url);
        Integer codigo = null;
        String erro = recusa;
        if (recusa == null) {
            try {
                HttpResponse<Void> r = http.send(montar(url, (String) e.get("evento"), id, (String) e.get("payload"), (String) e.get("segredo_cifrado"), ((Number) e.get("saida_id")).longValue()),
                        HttpResponse.BodyHandlers.discarding());
                codigo = r.statusCode();
                erro = codigo >= 200 && codigo < 300 ? null : "O destino respondeu " + codigo;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                erro = "Envio interrompido";
            } catch (Exception ex) {
                erro = "Falha de comunicação: " + ex.getClass().getSimpleName();
            }
        }
        if (erro == null) {
            terminar(id, "enviado", codigo, null);
        } else if (tentativas > ESPERA_MIN.length) {
            terminar(id, "erro", codigo, erro);
            avisoInfra.avisar("saida-erro-" + e.get("saida_id"), "O webhook de saída \"" + nome + "\" não conseguiu entregar um aviso depois de "
                    + tentativas + " tentativas. Veja em Infraestrutura > Integrações.");
        } else {
            jdbcTemplate.update("UPDATE integracao.entrega SET status = 'pendente', proxima_tentativa = ?, resposta_codigo = ?, erro = ? WHERE id = ?",
                    Timestamp.valueOf(LocalDateTime.now().plusMinutes(ESPERA_MIN[tentativas - 1])), codigo, erro, id);
        }
    }

    private HttpRequest montar(String url, String evento, Long entrega, String payload, String segredoCifrado, Long saidaId) {
        String carimbo = String.valueOf(System.currentTimeMillis() / 1000);
        byte[] corpo = payload.getBytes(StandardCharsets.UTF_8);
        String segredo = cifrador.decifrar(segredoCifrado, contexto(saidaId));
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .header("X-COTI-Evento", evento).header("X-COTI-Entrega", String.valueOf(entrega)).header("X-COTI-Timestamp", carimbo)
                .header("X-COTI-Signature", "sha256=" + EntradaRegras.assinar(segredo.getBytes(StandardCharsets.UTF_8), carimbo, corpo))
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo)).build();
    }

    private void terminar(Long id, String status, Integer codigo, String erro) {
        jdbcTemplate.update("UPDATE integracao.entrega SET status = ?, resposta_codigo = ?, erro = ?, enviado_em = CASE WHEN ? = 'enviado' THEN now() END WHERE id = ?",
                status, codigo, erro, status, id);
    }

    // ---------------------------------------------------------------- cadastro

    public List<IntegracaoVOs.Saida> listar() {
        return jdbcTemplate.query("SELECT id, nome, url, eventos, ativa FROM integracao.saida ORDER BY nome", (rs, i) -> {
            IntegracaoVOs.Saida s = new IntegracaoVOs.Saida();
            s.setId(rs.getLong("id"));
            s.setNome(rs.getString("nome"));
            s.setUrl(rs.getString("url"));
            s.setEventos(Arrays.asList(rs.getString("eventos").split(",")));
            s.setAtiva(rs.getBoolean("ativa"));
            return s;
        });
    }

    /** @return o segredo, só quando a saída é nova */
    public IntegracaoVOs.Salva salvar(IntegracaoVOs.Saida r, Long userId) {
        String recusa = DestinoRegras.motivoDeRecusa(r.getUrl());
        if (recusa != null) {
            throw exception(INVALIDA, recusa);
        }
        if (r.getEventos() == null || r.getEventos().isEmpty() || !VALIDOS.containsAll(r.getEventos())) {
            throw exception(INVALIDA, "Escolha os eventos");
        }
        if (!cifrador.configurado()) {
            throw exception(INVALIDA, "Defina CO_TI_CHAVE_MESTRA no servidor: sem ela o segredo não pode ser guardado");
        }
        String eventos = String.join(",", new TreeSet<>(r.getEventos()));
        if (r.getId() != null) {
            if (jdbcTemplate.update("UPDATE integracao.saida SET nome = ?, url = ?, eventos = ?, ativa = ? WHERE id = ?", r.getNome().strip(), r.getUrl().strip(),
                    eventos, !Boolean.FALSE.equals(r.getAtiva()), r.getId()) == 0) {
                throw exception(INVALIDA, "Saída não encontrada");
            }
            auditoria.registrar("integracao.saida_alterada", userId, null, Map.of("saida", r.getId(), "url", r.getUrl()));
            return new IntegracaoVOs.Salva(r.getId(), null);
        }
        Long id = jdbcTemplate.queryForObject("SELECT nextval('integracao.saida_seq')", Long.class);
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String segredo = HexFormat.of().formatHex(b);
        jdbcTemplate.update("INSERT INTO integracao.saida (id, nome, url, segredo_cifrado, eventos, ativa) VALUES (?, ?, ?, ?, ?, ?)", id, r.getNome().strip(), r.getUrl().strip(),
                cifrador.cifrar(segredo, contexto(id)), eventos, !Boolean.FALSE.equals(r.getAtiva()));
        auditoria.registrar("integracao.saida_criada", userId, null, Map.of("saida", id, "url", r.getUrl()));
        return new IntegracaoVOs.Salva(id, segredo);
    }

    public PageResult<IntegracaoVOs.Entrega> entregas(int pagina, int tamanho) {
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM integracao.entrega", Long.class);
        List<IntegracaoVOs.Entrega> lista = jdbcTemplate.query("SELECT d.*, s.nome FROM integracao.entrega d JOIN integracao.saida s ON s.id = d.saida_id ORDER BY d.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Timestamp env = rs.getTimestamp("enviado_em");
                    return new IntegracaoVOs.Entrega(rs.getLong("id"), rs.getString("nome"), rs.getString("evento"), rs.getLong("chamado_id"), rs.getString("status"),
                            rs.getInt("tentativas"), (Integer) rs.getObject("resposta_codigo"), rs.getString("erro"), rs.getTimestamp("criado_em").toLocalDateTime(),
                            env == null ? null : env.toLocalDateTime());
                }, tamanho, Math.max(0, pagina - 1) * tamanho);
        return new PageResult<>(lista, total == null ? 0 : total);
    }

    private static String contexto(Long saidaId) {
        return "integracao-saida:" + saidaId;
    }

}
