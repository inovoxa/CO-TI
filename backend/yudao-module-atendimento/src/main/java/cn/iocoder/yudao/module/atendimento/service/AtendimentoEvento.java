package cn.iocoder.yudao.module.atendimento.service;

/**
 * Algo mudou numa conversa. {@code tipo}: "conversa" (dados da conversa) ou "mensagem" (uma mensagem nova ou
 * atualizada, em {@code mensagemId}).
 */
public record AtendimentoEvento(String tipo, Long conversaId, Long mensagemId) {
}
