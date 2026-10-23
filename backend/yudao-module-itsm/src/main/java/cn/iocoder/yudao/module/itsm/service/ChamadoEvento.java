package cn.iocoder.yudao.module.itsm.service;

/**
 * Publicado (Spring ApplicationEvent) depois de cada mudança em chamado, já com a transação confirmada.
 * Notificações por e-mail, WhatsApp, telão e automações assinam este evento sem acoplar ao serviço.
 *
 * @param acao criado | atualizado | ator | acompanhamento | tarefa | solucao | solucao-aprovada |
 *             solucao-recusada | pendente | retomado | fechado | reaberto | excluido
 */
public record ChamadoEvento(Long chamadoId, String acao, Long userId) {
}
