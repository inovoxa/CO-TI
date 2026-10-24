package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pesquisa de satisfação mandada ao resolver a conversa (Chatwoot: CsatSurveyResponse).
 */
@TableName("atendimento.pesquisa")
@KeySequence("atendimento.pesquisa_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PesquisaDO extends BaseDO {

    @TableId
    private Long id;
    private Long conversaId;
    private Long caixaId;
    private Long agenteId;
    private Long contatoId;
    private LocalDateTime enviadaEm;
    /** 1 a 5; null = sem resposta */
    private Integer nota;
    private LocalDateTime respondidaEm;

}
