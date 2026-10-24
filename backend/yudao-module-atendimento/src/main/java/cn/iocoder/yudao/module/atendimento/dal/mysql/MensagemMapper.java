package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.MensagemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface MensagemMapper extends BaseMapperX<MensagemDO> {

    /** Última mensagem que o contato vê ou mandou (sem notas e registros), por conversa. */
    @Select({"<script>SELECT DISTINCT ON (conversa_id) * FROM atendimento.mensagem WHERE deleted = 0 AND tipo IN (0, 1, 3)",
            " AND privada = false AND conversa_id IN <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>",
            " ORDER BY conversa_id, id DESC</script>"})
    List<MensagemDO> ultimas(@Param("ids") Collection<Long> conversaIds);

    /** Mensagens do contato depois da última vista pela pessoa, por conversa. */
    @Select({"<script>SELECT m.conversa_id, count(*) AS n FROM atendimento.mensagem m",
            " LEFT JOIN atendimento.conversa_leitura l ON l.conversa_id = m.conversa_id AND l.user_id = #{userId}",
            " WHERE m.deleted = 0 AND m.tipo = 0 AND m.id &gt; COALESCE(l.lida_ate, 0)",
            " AND m.conversa_id IN <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>",
            " GROUP BY m.conversa_id</script>"})
    List<Map<String, Object>> naoLidas(@Param("ids") Collection<Long> conversaIds, @Param("userId") Long userId);

    /** Mesma mensagem já recebida nesta caixa (o provedor reenviou o webhook)? */
    @Select("SELECT count(*) FROM atendimento.mensagem m JOIN atendimento.conversa c ON c.id = m.conversa_id"
            + " WHERE m.id_externo = #{idExterno} AND c.caixa_id = #{caixaId}")
    long contarExterno(@Param("caixaId") Long caixaId, @Param("idExterno") String idExterno);

}
