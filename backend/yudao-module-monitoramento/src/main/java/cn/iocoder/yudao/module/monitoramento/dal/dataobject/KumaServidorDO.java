package cn.iocoder.yudao.module.monitoramento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Uptime Kuma lido ao vivo pelo {@code /metrics}. A chave de API (ou a senha) fica cifrada com a chave-mestra.
 */
@TableName("monitor.kuma_servidor")
@KeySequence("monitor.kuma_servidor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "chave")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KumaServidorDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String url;
    /** vazio com chave de API; preenchido só no login comum (Kuma sem chave de API) */
    private String usuario;
    private String chave;
    private Boolean ignorarTls;

}
