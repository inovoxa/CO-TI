package cn.iocoder.yudao.module.chat.service;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collection;

/**
 * Avisa as telas só depois de confirmar no banco, para não mostrar mensagem que a transação desfez. Se o aviso se
 * perder, nada se perde: a tela busca pelo número sequencial o que faltou ao reconectar.
 */
@Slf4j
@Component
public class ChatTempoReal {

    /** Tipos: chat-mensagem, chat-atualizada, chat-conversa e chat-lida. */
    public record Evento(String tipo, Object conteudo, Collection<Long> destinos) {
    }

    @Resource
    private WebSocketSenderApi webSocketSenderApi;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoMudar(Evento e) {
        for (Long u : e.destinos()) {
            try {
                webSocketSenderApi.sendObject(UserTypeEnum.ADMIN.getValue(), u, e.tipo(), e.conteudo());
            } catch (Exception ex) {
                log.warn("[aoMudar][{} para {}]", e.tipo(), u, ex);
            }
        }
    }

}
