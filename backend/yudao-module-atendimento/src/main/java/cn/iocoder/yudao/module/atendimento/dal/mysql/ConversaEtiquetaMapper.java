package cn.iocoder.yudao.module.atendimento.dal.mysql;

import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Etiquetas de cada conversa.
 */
@Mapper
public interface ConversaEtiquetaMapper {

    @Insert("INSERT INTO atendimento.conversa_etiqueta (conversa_id, etiqueta_id) VALUES (#{c}, #{e}) ON CONFLICT DO NOTHING")
    int incluir(@Param("c") Long conversaId, @Param("e") Long etiquetaId);

    @Delete("DELETE FROM atendimento.conversa_etiqueta WHERE conversa_id = #{c} AND etiqueta_id = #{e}")
    int remover(@Param("c") Long conversaId, @Param("e") Long etiquetaId);

    @Delete("DELETE FROM atendimento.conversa_etiqueta WHERE etiqueta_id = #{e}")
    int removerEtiqueta(@Param("e") Long etiquetaId);

    @Select({"<script>SELECT conversa_id, etiqueta_id FROM atendimento.conversa_etiqueta WHERE conversa_id IN",
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>"})
    List<Map<String, Object>> listar(@Param("ids") Collection<Long> conversaIds);

}
