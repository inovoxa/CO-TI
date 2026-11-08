package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Chave geral da IA e o provedor principal de cada uso (uma linha só, id = 1). Vazio = o primeiro ativo pela
 * prioridade.
 */
@TableName("atendimento.ia_config")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IaConfigDO extends BaseDO {

    public static final long ID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;
    private Boolean ativo;
    private Long provedorAssistenteId;
    private Long provedorCopilotoId;
    private Long provedorAssistenteReservaId;
    private Long provedorCopilotoReservaId;
    /** Assistente IA do portal: ligado à parte do atendimento, com provedor próprio */
    private Boolean internoAtivo;
    private Long provedorInternoId;
    private Long provedorInternoReservaId;

}
