package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ExecucaoAdDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ExecucaoAdMapper extends BaseMapperX<ExecucaoAdDO> {

    /** Prontas para rodar agora (inclui as que esperam nova tentativa). */
    @Select("SELECT id FROM itsm.execucao_ad WHERE status = 'pronta'"
            + " AND (proxima_tentativa IS NULL OR proxima_tentativa <= now()) ORDER BY id LIMIT #{limite}")
    List<Long> selectIdsProntas(@Param("limite") int limite);

    /** Reivindica a execução: só uma instância consegue passar de "pronta" para "executando". */
    @Update("UPDATE itsm.execucao_ad SET status = 'executando', tentativas = tentativas + 1, executado_em = now()"
            + " WHERE id = #{id} AND status = 'pronta'")
    int reivindicar(@Param("id") Long id);

    /**
     * Execuções que ficaram em "executando" (o servidor caiu no meio): viram erro. Não voltam para a fila sozinhas
     * porque a operação pode ter sido feita no AD; um técnico confere e repete.
     */
    @Update("UPDATE itsm.execucao_ad SET status = 'erro', mensagem = 'Execução interrompida (o servidor parou no meio). '"
            + " || 'Confira no AD se a operação foi feita antes de repetir.' WHERE status = 'executando'"
            + " AND executado_em < now() - make_interval(secs => #{segundos})")
    int marcarInterrompidas(@Param("segundos") int segundos);

    default ExecucaoAdDO selectByChamadoId(Long chamadoId) {
        return selectOne(ExecucaoAdDO::getChamadoId, chamadoId);
    }

}
