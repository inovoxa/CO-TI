package cn.iocoder.yudao.module.calendario.service;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Avisos da agenda no sino. Saem só depois que a mudança foi gravada, e uma falha no aviso nunca desfaz o compromisso.
 * O link do aviso abre o compromisso no Calendário.
 */
@Slf4j
@Component
public class AvisosAgenda {

    public static final String CONVITE = "coti_agenda_convite";
    public static final String ALTERADO = "coti_agenda_alterado";
    public static final String CANCELADO = "coti_agenda_cancelado";
    public static final String RETIRADO = "coti_agenda_retirado";
    public static final String RESPOSTA = "coti_agenda_resposta";

    @Resource
    private NotifySendService notifySendService;
    @Resource
    private WebSocketSenderApi webSocketSenderApi;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PlatformTransactionManager transactionManager;

    /** Para os convidados. {@code eventoId} nulo quando o compromisso não existe mais para eles (sem link). */
    public void avisar(Collection<Long> destinos, String modelo, Long autorId, Long eventoId, String titulo, String quando) {
        if (destinos.isEmpty()) {
            return;
        }
        List<Long> para = List.copyOf(destinos);
        depoisDeGravar(() -> {
            String autor = nome(autorId);
            for (Long id : para) {
                enviar(id, modelo, params(autor, eventoId, titulo, quando, null));
            }
        });
    }

    /** Para o dono: alguém respondeu ou saiu. */
    public void resposta(Long donoId, Long pessoaId, Long eventoId, String titulo, String acao, String quando) {
        depoisDeGravar(() -> enviar(donoId, RESPOSTA, params(nome(pessoaId), eventoId, titulo, quando, acao)));
    }

    private static Map<String, Object> params(String pessoa, Long eventoId, String titulo, String quando, String acao) {
        Map<String, Object> p = new HashMap<>();
        p.put("pessoa", pessoa);
        p.put("titulo", titulo.length() > 90 ? titulo.substring(0, 89) + "…" : titulo);
        p.put("quando", quando);
        if (acao != null) {
            p.put("acao", acao);
        }
        if (eventoId != null) {
            p.put("link", "/calendario?evento=" + eventoId);
        }
        return p;
    }

    private String nome(Long userId) {
        List<String> n = jdbcTemplate.queryForList("SELECT nickname FROM system_users WHERE id = ?", String.class, userId);
        return n.isEmpty() || n.get(0) == null ? "Alguém" : n.get(0);
    }

    private void enviar(Long userId, String modelo, Map<String, Object> params) {
        try {
            notifySendService.sendSingleNotifyToAdmin(userId, modelo, params);
        } catch (Exception e) {
            log.error("[enviar][aviso {} para {}]", modelo, userId, e);
            return;
        }
        try {
            webSocketSenderApi.sendObject(UserTypeEnum.ADMIN.getValue(), userId, "sino", Map.of("agenda", true));
        } catch (Exception e) {
            // o sino também consulta de tempos em tempos
            log.warn("[enviar][tempo real para {}: {}]", userId, e.getMessage());
        }
    }

    private void depoisDeGravar(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executar(r);
                }
            });
        } else {
            executar(r);
        }
    }

    /** Em transação nova: depois do commit a conexão da anterior ainda está presa a esta thread. */
    private void executar(Runnable r) {
        try {
            TransactionTemplate t = new TransactionTemplate(transactionManager);
            t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            t.executeWithoutResult(s -> r.run());
        } catch (Exception e) {
            log.error("[avisos da agenda]", e);
        }
    }

}
