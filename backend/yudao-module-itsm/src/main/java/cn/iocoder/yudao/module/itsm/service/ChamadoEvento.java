package cn.iocoder.yudao.module.itsm.service;

/**
 * Publicado (Spring ApplicationEvent) a cada mudança em chamado, dentro da transação da mudança.
 * Notificações por e-mail, WhatsApp, telão e automações assinam este evento sem acoplar ao serviço.
 *
 * @param acao  criado | atualizado | ator | acompanhamento | tarefa | solucao | solucionado | solucao-aprovada |
 *              solucao-recusada | pendente | retomado | fechado | reaberto | excluido | escalonado |
 *              aprovacao-solicitada | aprovado | recusado | fechado-recusa | satisfacao
 * @param refId interação (acompanhamento, tarefa, solução) ou participante (ator) a que a ação se refere
 */
public record ChamadoEvento(Long chamadoId, String acao, Long userId, Long refId) {

    public ChamadoEvento(Long chamadoId, String acao, Long userId) {
        this(chamadoId, acao, userId, null);
    }

}
