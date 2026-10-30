package cn.iocoder.yudao.module.atendimento.canal;

import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import org.springframework.stereotype.Component;

/**
 * Canal "widget": o assistente da tela de login. Não há provedor: a resposta fica na conversa e a página a busca
 * ({@link cn.iocoder.yudao.module.atendimento.service.WidgetService}).
 */
@Component
public class CanalWidget implements Canal {

    @Override
    public String tipo() {
        return "widget";
    }

    @Override
    public String enviar(CaixaDO caixa, ConversaDO conversa, ContatoDO contato, ContatoCanalDO endereco, MensagemDO mensagem) {
        return null;
    }

}
