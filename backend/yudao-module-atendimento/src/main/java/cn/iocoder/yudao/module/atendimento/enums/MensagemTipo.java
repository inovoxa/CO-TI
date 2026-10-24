package cn.iocoder.yudao.module.atendimento.enums;

/** Tipo de mensagem. Mesmos códigos do Chatwoot (Message#message_type). */
public final class MensagemTipo {

    /** Do contato */
    public static final int ENTRADA = 0;
    /** Da central (atendente, robô ou resposta automática) */
    public static final int SAIDA = 1;
    /** Registro do que aconteceu ("Ana resolveu a conversa") */
    public static final int ATIVIDADE = 2;
    /** Modelo aprovado (WhatsApp fora da janela de 24 h) */
    public static final int MODELO = 3;

    private MensagemTipo() {
    }

}
