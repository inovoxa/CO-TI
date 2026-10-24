package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.WebhookEntradaDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WebhookEntradaMapper extends BaseMapperX<WebhookEntradaDO> {

    @Select("SELECT id FROM atendimento.webhook_entrada WHERE status = 'pendente' AND deleted = 0"
            + " AND (proxima_tentativa IS NULL OR proxima_tentativa <= now()) ORDER BY id LIMIT #{limite}")
    List<Long> selectIdsPendentes(@Param("limite") int limite);

    /** Só uma instância processa cada webhook. */
    @Update("UPDATE atendimento.webhook_entrada SET status = 'processando', tentativas = tentativas + 1, proxima_tentativa = now()"
            + " WHERE id = #{id} AND status = 'pendente' AND (proxima_tentativa IS NULL OR proxima_tentativa <= now())")
    int reivindicar(@Param("id") Long id);

    /** Presos em "processando" (o servidor parou no meio) voltam para a fila. */
    @Update("UPDATE atendimento.webhook_entrada SET status = 'pendente' WHERE status = 'processando'"
            + " AND proxima_tentativa < now() - interval '10 minutes'")
    int destravar();

    /** O conteúdo das conversas fica nas mensagens: o corpo bruto não precisa ficar guardado. */
    @Delete("DELETE FROM atendimento.webhook_entrada WHERE status IN ('ok', 'erro') AND recebido_em < now() - interval '7 days'")
    int limparAntigos();

}
