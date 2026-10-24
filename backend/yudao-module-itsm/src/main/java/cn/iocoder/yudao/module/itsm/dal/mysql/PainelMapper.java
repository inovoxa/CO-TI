package cn.iocoder.yudao.module.itsm.dal.mysql;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Indicadores do Service Desk (GLPI: Stat e Dashboard). Toda consulta lê itsm.chamado com o apelido "c":
 * o filtro de escopo de unidade do perfil é aplicado pelo plugin de permissão de dados.
 * Status: 1 novo, 2 atribuído, 3 planejado, 4 pendente, 5 solucionado, 6 fechado.
 */
@Mapper
public interface PainelMapper {

    String FILTRO = "<if test=\"deptId != null\"> AND c.dept_id = #{deptId}</if>"
            + "<if test=\"categorias != null\"> AND c.categoria_id IN"
            + "<foreach collection=\"categorias\" item=\"x\" open=\"(\" separator=\",\" close=\")\">#{x}</foreach></if>";

    @Select("<script>SELECT"
            + " sum(CASE WHEN c.status &lt; 5 THEN 1 ELSE 0 END) AS abertos,"
            + " sum(CASE WHEN c.status = 1 THEN 1 ELSE 0 END) AS sem_tecnico,"
            + " sum(CASE WHEN c.status = 4 THEN 1 ELSE 0 END) AS pendentes,"
            + " sum(CASE WHEN c.status IN (1, 2, 3) AND c.prazo_solucao &lt; now() THEN 1 ELSE 0 END) AS vencidos,"
            + " sum(CASE WHEN c.aberto_em &gt;= #{de} AND c.aberto_em &lt; #{ate} THEN 1 ELSE 0 END) AS novos,"
            + " sum(CASE WHEN c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate} THEN 1 ELSE 0 END) AS solucionados,"
            + " sum(CASE WHEN c.fechado_em &gt;= #{de} AND c.fechado_em &lt; #{ate} THEN 1 ELSE 0 END) AS fechados,"
            + " sum(CASE WHEN c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate} AND c.prazo_solucao IS NOT NULL THEN 1 ELSE 0 END) AS com_prazo,"
            + " sum(CASE WHEN c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate} AND c.solucionado_em &lt;= c.prazo_solucao THEN 1 ELSE 0 END) AS no_prazo,"
            + " sum(CASE WHEN c.aberto_em &gt;= #{de} AND c.aberto_em &lt; #{ate} AND c.prazo_atendimento IS NOT NULL AND c.assumido_em IS NOT NULL THEN 1 ELSE 0 END) AS com_prazo_atendimento,"
            + " sum(CASE WHEN c.aberto_em &gt;= #{de} AND c.aberto_em &lt; #{ate} AND c.assumido_em &lt;= c.prazo_atendimento THEN 1 ELSE 0 END) AS atendidos_no_prazo,"
            + " avg(CASE WHEN c.aberto_em &gt;= #{de} AND c.aberto_em &lt; #{ate} AND c.assumido_em IS NOT NULL THEN c.tempo_assumir_seg END) AS media_assumir_seg,"
            + " avg(CASE WHEN c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate} THEN c.tempo_solucao_seg END) AS media_solucao_seg"
            + " FROM itsm.chamado c WHERE c.deleted = 0" + FILTRO + "</script>")
    Map<String, Object> resumo(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                               @Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

    @Select("<script>SELECT round(avg(s.nota)::numeric, 2) AS media, count(s.respondida_em) AS respondidas, count(*) AS enviadas"
            + " FROM itsm.satisfacao s JOIN itsm.chamado c ON c.id = s.chamado_id"
            + " WHERE c.deleted = 0 AND s.enviada_em &gt;= #{de} AND s.enviada_em &lt; #{ate}" + FILTRO + "</script>")
    Map<String, Object> satisfacao(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                   @Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

    /** Quantos por dia, pela coluna de data indicada (aberto_em ou solucionado_em). */
    @Select("<script>SELECT to_char(date_trunc('day', c.${coluna}), 'YYYY-MM-DD') AS dia, count(*) AS total"
            + " FROM itsm.chamado c WHERE c.deleted = 0 AND c.${coluna} &gt;= #{de} AND c.${coluna} &lt; #{ate}" + FILTRO
            + " GROUP BY 1 ORDER BY 1</script>")
    List<Map<String, Object>> porDia(@Param("coluna") String coluna, @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                     @Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

    @Select("<script>SELECT coalesce(k.nome_completo, 'Sem categoria') AS nome, count(*) AS total"
            + " FROM itsm.chamado c LEFT JOIN itsm.categoria k ON k.id = c.categoria_id"
            + " WHERE c.deleted = 0 AND c.aberto_em &gt;= #{de} AND c.aberto_em &lt; #{ate}" + FILTRO
            + " GROUP BY k.nome_completo ORDER BY total DESC LIMIT 10</script>")
    List<Map<String, Object>> porCategoria(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                           @Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

    @Select("<script>SELECT coalesce(o.nome, '—') AS nome, count(*) AS total"
            + " FROM itsm.chamado c LEFT JOIN itsm.origem o ON o.id = c.origem_id"
            + " WHERE c.deleted = 0 AND c.aberto_em &gt;= #{de} AND c.aberto_em &lt; #{ate}" + FILTRO
            + " GROUP BY o.nome ORDER BY total DESC</script>")
    List<Map<String, Object>> porOrigem(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                        @Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

    /** Abertos agora, por prioridade. */
    @Select("<script>SELECT c.prioridade AS prioridade, count(*) AS total FROM itsm.chamado c"
            + " WHERE c.deleted = 0 AND c.status &lt; 5" + FILTRO + " GROUP BY c.prioridade ORDER BY c.prioridade DESC</script>")
    List<Map<String, Object>> porPrioridade(@Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

    /** Abertos agora, pela idade. */
    @Select("<script>SELECT"
            + " sum(CASE WHEN now() - c.aberto_em &lt; interval '1 day' THEN 1 ELSE 0 END) AS ate_1d,"
            + " sum(CASE WHEN now() - c.aberto_em &gt;= interval '1 day' AND now() - c.aberto_em &lt; interval '3 days' THEN 1 ELSE 0 END) AS ate_3d,"
            + " sum(CASE WHEN now() - c.aberto_em &gt;= interval '3 days' AND now() - c.aberto_em &lt; interval '7 days' THEN 1 ELSE 0 END) AS ate_7d,"
            + " sum(CASE WHEN now() - c.aberto_em &gt;= interval '7 days' AND now() - c.aberto_em &lt; interval '30 days' THEN 1 ELSE 0 END) AS ate_30d,"
            + " sum(CASE WHEN now() - c.aberto_em &gt;= interval '30 days' THEN 1 ELSE 0 END) AS mais_30d"
            + " FROM itsm.chamado c WHERE c.deleted = 0 AND c.status &lt; 5" + FILTRO + "</script>")
    Map<String, Object> idadeDosAbertos(@Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

    /** Por técnico (pessoa atribuída): abertos agora e solucionados no período. */
    @Select("<script>SELECT a.user_id AS user_id,"
            + " sum(CASE WHEN c.status &lt; 5 THEN 1 ELSE 0 END) AS abertos,"
            + " sum(CASE WHEN c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate} THEN 1 ELSE 0 END) AS solucionados,"
            + " avg(CASE WHEN c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate} THEN c.tempo_solucao_seg END) AS media_solucao_seg"
            + " FROM itsm.chamado c JOIN itsm.chamado_ator a ON a.chamado_id = c.id AND a.deleted = 0 AND a.tipo = 2 AND a.user_id IS NOT NULL"
            + " WHERE c.deleted = 0 AND (c.status &lt; 5 OR (c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate}))" + FILTRO
            + " GROUP BY a.user_id ORDER BY solucionados DESC, abertos DESC LIMIT 20</script>")
    List<Map<String, Object>> porTecnico(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                         @Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

    @Select("<script>SELECT e.nome AS nome,"
            + " sum(CASE WHEN c.status &lt; 5 THEN 1 ELSE 0 END) AS abertos,"
            + " sum(CASE WHEN c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate} THEN 1 ELSE 0 END) AS solucionados"
            + " FROM itsm.chamado c JOIN itsm.chamado_ator a ON a.chamado_id = c.id AND a.deleted = 0 AND a.tipo = 2"
            + " JOIN itsm.equipe e ON e.id = a.equipe_id"
            + " WHERE c.deleted = 0 AND (c.status &lt; 5 OR (c.solucionado_em &gt;= #{de} AND c.solucionado_em &lt; #{ate}))" + FILTRO
            + " GROUP BY e.nome ORDER BY abertos DESC, solucionados DESC LIMIT 20</script>")
    List<Map<String, Object>> porEquipe(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate,
                                        @Param("deptId") Long deptId, @Param("categorias") List<Long> categorias);

}
