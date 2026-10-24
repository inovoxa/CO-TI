package cn.iocoder.yudao.module.itsm.dal.mysql;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Votos "ajudou?" — chave composta, sem o CRUD do MyBatis-Plus. */
@Mapper
public interface KbVotoMapper {

    @Insert("INSERT INTO itsm.kb_voto (artigo_id, user_id, util, votado_em) VALUES (#{artigoId}, #{userId}, #{util}, now())"
            + " ON CONFLICT (artigo_id, user_id) DO UPDATE SET util = EXCLUDED.util, votado_em = now()")
    void votar(@Param("artigoId") Long artigoId, @Param("userId") Long userId, @Param("util") boolean util);

    @Select("SELECT util FROM itsm.kb_voto WHERE artigo_id = #{artigoId} AND user_id = #{userId}")
    Boolean meuVoto(@Param("artigoId") Long artigoId, @Param("userId") Long userId);

}
