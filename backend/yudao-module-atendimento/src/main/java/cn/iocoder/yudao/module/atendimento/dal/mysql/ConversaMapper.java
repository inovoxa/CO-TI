package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ConversaMapper extends BaseMapperX<ConversaDO> {

    @Select("SELECT * FROM atendimento.conversa WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ConversaDO selectByIdParaAtualizar(@Param("id") Long id);

    /** Conversas abertas por atendente: na caixa e no total (para a atribuição automática). */
    @Select({"<script>SELECT agente_id, count(*) AS total, sum(CASE WHEN caixa_id = #{caixaId} THEN 1 ELSE 0 END) AS na_caixa",
            " FROM atendimento.conversa WHERE deleted = 0 AND status = 0",
            " AND agente_id IN <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>",
            " GROUP BY agente_id</script>"})
    List<Map<String, Object>> abertasPorAgente(@Param("caixaId") Long caixaId, @Param("ids") Collection<Long> agentes);

}
