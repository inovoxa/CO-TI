package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.EnvioDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EnvioMapper extends BaseMapperX<EnvioDO> {

    @Select("SELECT id FROM atendimento.envio WHERE status = 'pendente'"
            + " AND (proxima_tentativa IS NULL OR proxima_tentativa <= now()) ORDER BY id LIMIT #{limite}")
    List<Long> selectIdsPendentes(@Param("limite") int limite);

    /** Só uma instância consegue pegar o envio. */
    @Update("UPDATE atendimento.envio SET status = 'enviando', tentativas = tentativas + 1, proxima_tentativa = now()"
            + " WHERE id = #{id} AND status = 'pendente'")
    int reivindicar(@Param("id") Long id);

    /** Envios presos em "enviando" (o servidor parou no meio) voltam para a fila. */
    @Update("UPDATE atendimento.envio SET status = 'pendente' WHERE status = 'enviando'"
            + " AND proxima_tentativa < now() - interval '10 minutes'")
    int destravar();

}
