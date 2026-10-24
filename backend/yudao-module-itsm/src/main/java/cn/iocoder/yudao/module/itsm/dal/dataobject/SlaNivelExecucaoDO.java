package cn.iocoder.yudao.module.itsm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Nível de escalonamento já executado num chamado (não repete). */
@TableName("itsm.sla_nivel_execucao")
@KeySequence("itsm.sla_nivel_execucao_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaNivelExecucaoDO {

    @TableId
    private Long id;
    private Long chamadoId;
    private Long nivelId;
    private LocalDateTime executadoEm;

}
