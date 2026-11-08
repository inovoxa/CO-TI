package cn.iocoder.yudao.module.infraestrutura.dal;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Um sistema externo que pode avisar o CO-TI, e o que fazer com o aviso. Os campos opcionais aceitam ser limpos na tela (ALWAYS). */
@TableName("integracao.entrada")
@KeySequence("integracao.entrada_seq")
@Data
public class IntegracaoEntradaDO {

    @TableId
    private Long id;
    private String codigo;
    private String nome;
    private Boolean ativa;
    /** simulacao | confirmacao | automatico */
    private String modo;
    /** hmac | bearer */
    private String autenticacao;
    private String segredoCifrado;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ipsPermitidos;
    private Long formularioId;
    private Long solicitanteId;
    private String atributoAd;
    private String caminhoId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String caminhoNome;
    private Boolean conferirNome;
    private String caminhoSituacao;
    private String valoresDesligar;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String valoresCancelar;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String caminhoData;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String caminhoChave;
    /** JSON: campo do formulário → caminho no JSON recebido */
    private String mapa;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String gruposProtegidos;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String loginsProtegidos;
    private Integer limiteDia;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

}
