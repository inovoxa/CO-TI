package cn.iocoder.yudao.module.infraestrutura.dal;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Um aviso recebido. O conteúdo fica cifrado: pode ter dados pessoais. */
@TableName("integracao.evento")
@KeySequence("integracao.evento_seq")
@Data
public class IntegracaoEventoDO {

    @TableId
    private Long id;
    private Long entradaId;
    private String chave;
    private String ip;
    private String payloadCifrado;
    private String idHash;
    /** recebido | processando | simulado | ignorado | chamado | executando | agendado | confirmacao | cancelado | erro */
    private String status;
    private String resultado;
    private String login;
    private Long chamadoId;
    private Integer tentativas;
    private LocalDateTime proximaTentativa;
    private LocalDateTime recebidoEm;
    private LocalDateTime processadoEm;

}
