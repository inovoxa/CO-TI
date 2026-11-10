package cn.iocoder.yudao.module.monitoramento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Servidor Docker acessado pela API remota. A chave do cliente fica cifrada com a chave-mestra.
 */
@TableName("monitor.docker_host")
@KeySequence("monitor.docker_host_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "chave")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerHostDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String url;
    private String ca;
    private String certificado;
    private String chave;

}
