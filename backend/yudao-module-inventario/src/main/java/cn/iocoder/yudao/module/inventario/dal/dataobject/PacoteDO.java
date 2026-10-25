package cn.iocoder.yudao.module.inventario.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Pacote de software para os agentes OCS instalarem (teledeploy).
 */
@TableName("cmdb.pacote")
@KeySequence("cmdb.pacote_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacoteDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String descricao;
    private String plataforma;
    private String acao;
    private String comando;
    private String caminho;
    private Integer prioridade;
    private Boolean notificar;
    private String notificarTexto;
    private String fileid;
    private String arquivoNome;
    private Long tamanho;
    private String md5;
    private String sha256;
    private Integer fragmentos;
    private Boolean ativo;
    private Long userId;

}
