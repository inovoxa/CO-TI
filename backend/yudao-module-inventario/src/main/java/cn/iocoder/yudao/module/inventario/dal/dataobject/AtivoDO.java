package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Ativo do CMDB: computador, notebook, servidor, impressora... (um registro por equipamento).
 */
@TableName("cmdb.ativo")
@KeySequence("cmdb.ativo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtivoDO extends BaseDO {

    @TableId
    private Long id;
    private String tipo;
    private String nome;
    private String situacao;
    private String patrimonio;
    private String numeroSerie;
    private String fabricante;
    private String modelo;
    private String uuid;
    private String soNome;
    private String soVersao;
    private String soArquitetura;
    private Boolean soSemSuporte;
    private String cpu;
    private Integer cpuNucleos;
    private Integer memoriaMb;
    private Long discoTotalMb;
    private Long discoLivreMb;
    private String ip;
    private String mac;
    private String dominio;
    private String usuarioLogin;
    private Long usuarioId;
    private Long deptId;
    private Boolean deptManual;
    private String antivirus;
    private String origem;
    private String deviceId;
    private String agente;
    private LocalDateTime primeiroInventario;
    private LocalDateTime ultimoInventario;
    private LocalDateTime ultimoContato;
    private String observacao;
    /** Contador de páginas (impressoras, pelo SNMP) */
    private Long paginas;
    /** sysLocation do SNMP */
    private String localizacao;

}
