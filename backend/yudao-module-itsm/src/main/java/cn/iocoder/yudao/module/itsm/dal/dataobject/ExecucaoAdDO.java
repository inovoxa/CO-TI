package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Operação no Active Directory pedida por formulário (substitui chamados_log + WF14 do n8n).
 */
@TableName("itsm.execucao_ad")
@KeySequence("itsm.execucao_ad_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecucaoAdDO {

    @TableId
    private Long id;
    private Long chamadoId;
    private String acao;
    /** JSON enviado ao script */
    private String dados;
    /** aguardando_aprovacao | aguardando_confirmacao | pronta | executando | sucesso | erro | recusada | cancelada */
    private String status;
    private Integer tentativas;
    private java.time.LocalDateTime proximaTentativa;
    /** JSON devolvido pelo script, sem a senha */
    private String resultado;
    private String mensagem;
    /** Senha provisória cifrada com a chave-mestra */
    private String senhaCifrada;
    private java.time.LocalDateTime senhaVistaEm;
    private java.time.LocalDateTime criadoEm;
    private java.time.LocalDateTime executadoEm;
    /** Pedido vindo de integração: no sucesso, fecha o chamado sem esperar o solicitante e desativa a conta no portal */
    private Boolean fecharAoConcluir;

}
