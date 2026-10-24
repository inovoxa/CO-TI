package cn.iocoder.yudao.module.atendimento.canal;

import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ContatoCanalDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ContatoDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.MensagemDO;

/**
 * Um canal de atendimento (API, WhatsApp, Telegram...). Cada implementação sabe entregar uma mensagem; a entrada
 * chega pelo controlador do canal e passa por {@code ConversaService.receber}.
 */
public interface Canal {

    /** O valor de {@code caixa.canal} que este canal atende. */
    String tipo();

    /**
     * Entrega a mensagem ao contato.
     *
     * @return id da mensagem no canal (pode ser null)
     * @throws FalhaDefinitiva erro que não adianta repetir (número inexistente, mensagem recusada)
     * @throws Exception erro passageiro (rede, provedor fora): a fila tenta de novo
     */
    String enviar(CaixaDO caixa, ConversaDO conversa, ContatoDO contato, ContatoCanalDO endereco, MensagemDO mensagem) throws Exception;

    /** Erro que não adianta repetir. */
    class FalhaDefinitiva extends RuntimeException {
        public FalhaDefinitiva(String mensagem) {
            super(mensagem);
        }
    }

}
