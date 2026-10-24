package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ConversaMapper;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * Avisa as telas abertas pelo WebSocket, depois que a mudança foi confirmada no banco (nada de mensagem "fantasma"
 * que some porque a transação desfez). Tipos: {@code atendimento-conversa} (resumo da conversa) e
 * {@code atendimento-mensagem} ({conversaId, mensagem}). Vão para os membros da caixa, o atendente e a supervisão.
 */
@Slf4j
@Component
public class TempoReal {

    @Resource
    private WebSocketSenderApi webSocketSenderApi;
    @Resource
    private ConversaService conversaService;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private Acesso acesso;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoMudar(AtendimentoEvento e) {
        try {
            ConversaDO c = conversaMapper.selectById(e.conversaId());
            if (c == null) {
                return;
            }
            Object conteudo;
            String tipo;
            if ("mensagem".equals(e.tipo())) {
                AtendimentoVOs.Mensagem m = conversaService.mensagem(e.mensagemId());
                if (m == null) {
                    return;
                }
                tipo = "atendimento-mensagem";
                conteudo = Map.of("conversaId", c.getId(), "mensagem", m);
            } else {
                tipo = "atendimento-conversa";
                conteudo = conversaService.resumos(List.of(c), null).get(0);
            }
            for (Long u : acesso.interessados(c.getCaixaId(), c.getAgenteId())) {
                webSocketSenderApi.sendObject(UserTypeEnum.ADMIN.getValue(), u, tipo, conteudo);
            }
        } catch (Exception ex) {
            // aviso em tempo real não pode atrapalhar o atendimento; a tela recarrega sozinha
            log.warn("[aoMudar][{} conversa {}]", e.tipo(), e.conversaId(), ex);
        }
    }

}
