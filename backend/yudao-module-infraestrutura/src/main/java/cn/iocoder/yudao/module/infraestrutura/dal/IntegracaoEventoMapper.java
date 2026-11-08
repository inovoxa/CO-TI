package cn.iocoder.yudao.module.infraestrutura.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IntegracaoEventoMapper extends BaseMapperX<IntegracaoEventoDO> {

    @Select("SELECT id FROM integracao.evento WHERE status = 'recebido'"
            + " AND (proxima_tentativa IS NULL OR proxima_tentativa <= now()) ORDER BY id LIMIT #{limite}")
    List<Long> selectIdsNaFila(@Param("limite") int limite);

    /** Só uma instância consegue pegar o evento. */
    @Update("UPDATE integracao.evento SET status = 'processando', tentativas = tentativas + 1, proxima_tentativa = now()"
            + " WHERE id = #{id} AND status = 'recebido'")
    int reivindicar(@Param("id") Long id);

    /** O servidor caiu no meio: o chamado e o evento gravam juntos, então é seguro processar de novo. */
    @Update("UPDATE integracao.evento SET status = 'recebido' WHERE status = 'processando'"
            + " AND proxima_tentativa < now() - interval '10 minutes'")
    int devolverPresos();

    /** Bloqueios já pedidos: o limite por dia conta também os que esperam a data. */
    @Select("SELECT count(*) FROM integracao.evento WHERE entrada_id = #{entradaId} AND status IN ('executando', 'agendado')"
            + " AND processado_em > now() - interval '24 hours'")
    int contarBloqueios24h(@Param("entradaId") Long entradaId);

    /** Evento anterior da mesma pessoa que já virou chamado: o RH pode reenviar a situação sem repetir o chamado. */
    @Select("SELECT chamado_id FROM integracao.evento WHERE entrada_id = #{entradaId} AND id_hash = #{idHash} AND id <> #{id}"
            + " AND status IN ('chamado', 'executando', 'agendado', 'confirmacao')"
            + " AND recebido_em > now() - interval '30 days' ORDER BY id DESC LIMIT 1")
    Long selectChamadoAnterior(@Param("entradaId") Long entradaId, @Param("idHash") String idHash, @Param("id") Long id);

    @Select("SELECT * FROM integracao.evento WHERE entrada_id = #{entradaId} AND id_hash = #{idHash}"
            + " AND status IN ('agendado', 'confirmacao')")
    List<IntegracaoEventoDO> selectPendentesDaPessoa(@Param("entradaId") Long entradaId, @Param("idHash") String idHash);

}
