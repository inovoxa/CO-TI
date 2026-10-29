package cn.iocoder.yudao.module.monitoramento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.BatidaDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface MonMonitorMapper extends BaseMapperX<MonitorDO> {

    @Insert("INSERT INTO monitor.batida (monitor_id, momento, estado, latencia_ms, msg, importante)"
            + " VALUES (#{monitorId}, #{momento}, #{estado}, #{latenciaMs}, #{msg}, #{importante})")
    void inserirBatida(BatidaDO b);

    /** Soma a batida no total da hora (pendente conta como fora; manutenção fica de fora da disponibilidade). */
    @Insert("INSERT INTO monitor.batida_hora (monitor_id, hora, no_ar, fora, manutencao, soma_latencia, n_latencia)"
            + " VALUES (#{monitorId}, date_trunc('hour', CAST(#{momento} AS timestamp)), #{noAr}, #{fora}, #{manutencao}, #{latencia}, #{nLatencia})"
            + " ON CONFLICT (monitor_id, hora) DO UPDATE SET no_ar = batida_hora.no_ar + EXCLUDED.no_ar, fora = batida_hora.fora + EXCLUDED.fora,"
            + " manutencao = batida_hora.manutencao + EXCLUDED.manutencao, soma_latencia = batida_hora.soma_latencia + EXCLUDED.soma_latencia,"
            + " n_latencia = batida_hora.n_latencia + EXCLUDED.n_latencia")
    void somarHora(@Param("monitorId") Long monitorId, @Param("momento") LocalDateTime momento, @Param("noAr") int noAr,
                   @Param("fora") int fora, @Param("manutencao") int manutencao, @Param("latencia") long latencia,
                   @Param("nLatencia") int nLatencia);

    @Select("SELECT * FROM monitor.batida WHERE monitor_id = #{monitorId} ORDER BY momento DESC LIMIT #{limite}")
    List<BatidaDO> ultimas(@Param("monitorId") Long monitorId, @Param("limite") int limite);

    /** As últimas N batidas de cada monitor (das últimas 48 h), mais recente primeiro. */
    @Select({"<script>SELECT monitor_id, momento, estado, latencia_ms, msg, importante FROM (",
            "SELECT b.*, row_number() OVER (PARTITION BY monitor_id ORDER BY momento DESC) AS rn FROM monitor.batida b",
            " WHERE b.momento > now() - interval '48 hours' AND b.monitor_id IN",
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>",
            ") x WHERE rn &lt;= #{n} ORDER BY monitor_id, momento DESC</script>"})
    List<BatidaDO> ultimasDeVarios(@Param("ids") Collection<Long> ids, @Param("n") int n);

    /** Mudanças de estado (eventos), mais recente primeiro. */
    @Select({"<script>SELECT * FROM monitor.batida WHERE importante",
            "<if test='monitorId != null'> AND monitor_id = #{monitorId}</if>",
            "<if test='ids != null'> AND monitor_id IN <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></if>",
            " AND momento > #{desde} ORDER BY momento DESC LIMIT #{limite}</script>"})
    List<BatidaDO> eventos(@Param("monitorId") Long monitorId, @Param("ids") Collection<Long> ids, @Param("desde") LocalDateTime desde,
                           @Param("limite") int limite);

    /** Totais por monitor desde um momento: no_ar, fora, soma_latencia, n_latencia. */
    @Select({"<script>SELECT monitor_id, sum(no_ar) AS no_ar, sum(fora) AS fora, sum(soma_latencia) AS soma_latencia, sum(n_latencia) AS n_latencia",
            " FROM monitor.batida_hora WHERE hora >= date_trunc('hour', CAST(#{desde} AS timestamp)) AND monitor_id IN",
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach> GROUP BY monitor_id</script>"})
    List<Map<String, Object>> totais(@Param("ids") Collection<Long> ids, @Param("desde") LocalDateTime desde);

    /** Latência e quedas agrupadas em intervalos (gráfico). {@code intervalo} vem só do código. */
    @Select("SELECT time_bucket(CAST(#{intervalo} AS interval), momento) AS momento, round(avg(latencia_ms) FILTER (WHERE estado = 1)) AS media,"
            + " min(latencia_ms) FILTER (WHERE estado = 1) AS minima, max(latencia_ms) FILTER (WHERE estado = 1) AS maxima,"
            + " count(*) FILTER (WHERE estado IN (0, 2)) AS falhas, count(*) AS total"
            + " FROM monitor.batida WHERE monitor_id = #{monitorId} AND momento > #{desde} GROUP BY 1 ORDER BY 1")
    List<Map<String, Object>> serie(@Param("monitorId") Long monitorId, @Param("desde") LocalDateTime desde, @Param("intervalo") String intervalo);

    // função dona da tabela (V45): drop_chunks exige ser dono, e a aplicação conecta como coti_app
    @Select("SELECT public.coti_apagar_pedacos('monitor.batida', CAST(#{limite} AS timestamp))")
    int apagarBatidasAntigas(@Param("limite") LocalDateTime limite);

    @Delete("DELETE FROM monitor.batida_hora WHERE hora < #{limite}")
    int apagarHorasAntigas(@Param("limite") LocalDateTime limite);

    @Delete("DELETE FROM monitor.batida WHERE monitor_id = #{monitorId}")
    void apagarBatidas(@Param("monitorId") Long monitorId);

}
