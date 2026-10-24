package cn.iocoder.yudao.module.atendimento.dal.mysql;

import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Disponibilidade dos atendentes (atendimento.agente_status).
 */
@Mapper
public interface AgenteStatusMapper {

    @Insert("INSERT INTO atendimento.agente_status (user_id, disponibilidade, ultimo_sinal) VALUES (#{userId}, #{disp}, now())"
            + " ON CONFLICT (user_id) DO UPDATE SET disponibilidade = EXCLUDED.disponibilidade, ultimo_sinal = now()")
    void definir(@Param("userId") Long userId, @Param("disp") String disponibilidade);

    @Update("UPDATE atendimento.agente_status SET ultimo_sinal = now() WHERE user_id = #{userId}")
    int sinal(@Param("userId") Long userId);

    @Update("UPDATE atendimento.agente_status SET ultima_atribuicao = now() WHERE user_id = #{userId}")
    int marcarAtribuicao(@Param("userId") Long userId);

    @Select({"<script>SELECT user_id, disponibilidade, ultimo_sinal, ultima_atribuicao FROM atendimento.agente_status",
            " WHERE user_id IN <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>"})
    List<Map<String, Object>> listar(@Param("ids") Collection<Long> ids);

}
