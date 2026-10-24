package cn.iocoder.yudao.module.itsm.dal.mysql;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * Mapa "id na origem → id no CO-TI" e os ajustes de numeração do importador.
 *
 * <p>Os nomes de tabela e de sequência entram com ${...}: só o importador chama, sempre com constantes do código.
 */
@Mapper
public interface ImportacaoMapaMapper {

    @Select("SELECT id_origem, id_destino FROM itsm.importacao_mapa WHERE origem = #{origem} AND tipo = #{tipo}")
    List<Map<String, Object>> todos(@Param("origem") String origem, @Param("tipo") String tipo);

    @Insert("INSERT INTO itsm.importacao_mapa (origem, tipo, id_origem, id_destino) VALUES (#{origem}, #{tipo}, #{idOrigem}, #{idDestino})"
            + " ON CONFLICT DO NOTHING")
    int gravar(@Param("origem") String origem, @Param("tipo") String tipo, @Param("idOrigem") String idOrigem,
               @Param("idDestino") Long idDestino);

    /** Todos os ids da tabela, inclusive excluídos (ocupam a chave). */
    @Select("SELECT id FROM ${tabela}")
    List<Long> ids(@Param("tabela") String tabela);

    @Select("SELECT count(*) FROM ${tabela} WHERE id = #{id}")
    long contar(@Param("tabela") String tabela, @Param("id") Long id);

    @Update("UPDATE ${tabela} SET id = #{novo} WHERE id = #{atual}")
    int trocarId(@Param("tabela") String tabela, @Param("atual") Long atual, @Param("novo") Long novo);

    /** Garante que a sequência passe do maior id da tabela e do mínimo pedido. */
    @Select("SELECT setval('${sequencia}', GREATEST((SELECT COALESCE(max(id), 0) FROM ${tabela}), #{minimo}, 1))")
    Long ajustarSequencia(@Param("sequencia") String sequencia, @Param("tabela") String tabela, @Param("minimo") long minimo);

}
