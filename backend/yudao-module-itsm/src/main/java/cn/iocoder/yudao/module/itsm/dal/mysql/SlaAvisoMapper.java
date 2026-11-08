package cn.iocoder.yudao.module.itsm.dal.mysql;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SlaAvisoMapper {

    /** @return 1 se este é o primeiro aviso deste prazo; com mais de uma instância, só uma ganha */
    @Insert("INSERT INTO itsm.sla_aviso (chamado_id, tipo, prazo) VALUES (#{chamadoId}, #{tipo}, #{prazo}) ON CONFLICT DO NOTHING")
    int registrar(@Param("chamadoId") Long chamadoId, @Param("tipo") String tipo, @Param("prazo") LocalDateTime prazo);

}
