package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.EventoRelatorioDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Consultas dos relatórios do atendimento. Filtros opcionais: caixa e atendente. Conversas trazidas do histórico do
 * WhatsApp (coexistência) não contam: não foram atendidas aqui.
 */
@Mapper
public interface EventoRelatorioMapper extends BaseMapperX<EventoRelatorioDO> {

    String FILTRO_CONVERSA = "<if test='caixaId != null'> AND c.caixa_id = #{caixaId}</if>"
            + "<if test='agenteId != null'> AND c.agente_id = #{agenteId}</if>";
    String SEM_HISTORICO = " AND NOT EXISTS (SELECT 1 FROM atendimento.mensagem h WHERE h.conversa_id = c.id AND h.deleted = 0"
            + " AND h.dados LIKE '%\"historico\":true%')";
    String MENSAGEM_NAO_HISTORICO = " AND m.dados NOT LIKE '%\"historico\":true%'";

    @Select({"<script>SELECT c.create_time::date AS dia, count(*) AS n FROM atendimento.conversa c",
            " WHERE c.deleted = 0 AND c.create_time &gt;= #{de} AND c.create_time &lt; #{ate}", FILTRO_CONVERSA, SEM_HISTORICO,
            " GROUP BY 1 ORDER BY 1</script>"})
    List<Map<String, Object>> novasPorDia(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                          @Param("caixaId") Long caixaId, @Param("agenteId") Long agenteId);

    @Select({"<script>SELECT c.status, (c.agente_id IS NULL) AS sem_agente, count(*) AS n FROM atendimento.conversa c",
            " WHERE c.deleted = 0 AND c.status IN (0, 2)", FILTRO_CONVERSA, " GROUP BY 1, 2</script>"})
    List<Map<String, Object>> emAndamento(@Param("caixaId") Long caixaId, @Param("agenteId") Long agenteId);

    @Select({"<script>SELECT e.tipo, e.caixa_id, e.agente_id, e.valor_seg, e.valor_util_seg, e.ocorrido_em::date AS dia",
            " FROM atendimento.evento_relatorio e WHERE e.deleted = 0 AND e.ocorrido_em &gt;= #{de} AND e.ocorrido_em &lt; #{ate}",
            "<if test='caixaId != null'> AND e.caixa_id = #{caixaId}</if><if test='agenteId != null'> AND e.agente_id = #{agenteId}</if>",
            "</script>"})
    List<Map<String, Object>> eventos(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                      @Param("caixaId") Long caixaId, @Param("agenteId") Long agenteId);

    /** Mensagens trocadas (sem notas, registros e histórico importado) */
    @Select({"<script>SELECT m.tipo, count(*) AS n FROM atendimento.mensagem m JOIN atendimento.conversa c ON c.id = m.conversa_id",
            " WHERE m.deleted = 0 AND m.privada = false AND m.tipo IN (0, 1) AND m.create_time &gt;= #{de} AND m.create_time &lt; #{ate}",
            MENSAGEM_NAO_HISTORICO, FILTRO_CONVERSA, " GROUP BY m.tipo</script>"})
    List<Map<String, Object>> mensagens(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                        @Param("caixaId") Long caixaId, @Param("agenteId") Long agenteId);

    /** Mensagens dos contatos por dia da semana (1 = segunda) e hora */
    @Select({"<script>SELECT extract(isodow FROM m.create_time)::int AS dia, extract(hour FROM m.create_time)::int AS hora, count(*) AS n",
            " FROM atendimento.mensagem m JOIN atendimento.conversa c ON c.id = m.conversa_id",
            " WHERE m.deleted = 0 AND m.tipo = 0 AND m.create_time &gt;= #{de} AND m.create_time &lt; #{ate}",
            MENSAGEM_NAO_HISTORICO, FILTRO_CONVERSA, " GROUP BY 1, 2</script>"})
    List<Map<String, Object>> entradasPorHora(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                              @Param("caixaId") Long caixaId, @Param("agenteId") Long agenteId);

    @Select({"<script>SELECT p.id, p.conversa_id, p.caixa_id, p.agente_id, p.contato_id, p.nota, p.enviada_em, p.respondida_em",
            " FROM atendimento.pesquisa p WHERE p.deleted = 0 AND p.enviada_em &gt;= #{de} AND p.enviada_em &lt; #{ate}",
            "<if test='caixaId != null'> AND p.caixa_id = #{caixaId}</if><if test='agenteId != null'> AND p.agente_id = #{agenteId}</if>",
            " ORDER BY p.id DESC</script>"})
    List<Map<String, Object>> pesquisas(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                        @Param("caixaId") Long caixaId, @Param("agenteId") Long agenteId);

}
