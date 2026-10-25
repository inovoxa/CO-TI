package cn.iocoder.yudao.module.monitoramento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Monitor de disponibilidade (HTTP, TCP, ping, DNS, push, manual) e o estado atual.
 */
@TableName("monitor.monitor")
@KeySequence("monitor.monitor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String descricao;
    private String tipo;
    private String alvo;
    private Integer porta;
    private String metodo;
    private String corpo;
    private String cabecalhos;
    private String codigosAceitos;
    private String palavra;
    private Boolean palavraInvertida;
    private String jsonCaminho;
    private String jsonOperador;
    private String jsonEsperado;
    private String dnsTipo;
    private String dnsServidor;
    private String dnsEsperado;
    private Integer intervaloS;
    private Integer tentativas;
    private Integer intervaloTentativaS;
    private Integer tempoLimiteS;
    private Integer reenviarACada;
    private Boolean invertido;
    private Boolean ignorarTls;
    private Integer maxRedirecionamentos;
    private Integer lentoMs;
    private Integer certAvisoDias;
    private String pushToken;
    private Long unidadeId;
    private String papel;
    private Long deptId;
    private Boolean abrirChamado;
    private Long chamadoCategoriaId;
    private Integer chamadoUrgencia;
    private Boolean chamadoSolucionar;
    private Boolean ativo;
    private Integer estado;
    private LocalDateTime estadoDesde;
    private LocalDateTime ultimaVerificacao;
    private Integer ultimaLatenciaMs;
    private String ultimaMsg;
    private Integer tentativaAtual;
    private Integer quedasSeguidas;
    private Integer certDias;
    private LocalDateTime certValidade;
    private LocalDateTime certAvisadoValidade;
    private Long chamadoId;
    private Long kumaId;
    /** Coluna gerada no banco: fora do ar (0), pendente (1), o resto (2); para ordenar as listas */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer ordem;

}
