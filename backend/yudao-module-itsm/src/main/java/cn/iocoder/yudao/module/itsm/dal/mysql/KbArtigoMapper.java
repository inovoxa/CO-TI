package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.KbVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.KbArtigoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface KbArtigoMapper extends BaseMapperX<KbArtigoDO> {

    /*
     * q já vem limpo pelo BuscaTexto (só palavras e operadores & | :*), mas vai sempre como parâmetro.
     * somenteVisiveis: publicado e dentro do período; somenteFaq: o que um usuário comum pode ler.
     */
    String FILTRO = "FROM itsm.kb_artigo a WHERE a.deleted = 0"
            + "<if test=\"q != null\"> AND a.busca @@ to_tsquery('portuguese', #{q})</if>"
            + "<if test=\"categorias != null\"> AND a.categoria_id IN"
            + "<foreach collection=\"categorias\" item=\"c\" open=\"(\" separator=\",\" close=\")\">#{c}</foreach></if>"
            + "<if test=\"somenteVisiveis\"> AND a.publicado AND (a.inicio_publicacao IS NULL OR a.inicio_publicacao &lt;= now())"
            + " AND (a.fim_publicacao IS NULL OR a.fim_publicacao &gt; now())</if>"
            + "<if test=\"somenteFaq\"> AND a.faq</if>"
            + "<if test=\"faq != null\"> AND a.faq = #{faq}</if>"
            + "<if test=\"publicado != null\"> AND a.publicado = #{publicado}</if>";

    @Select("<script>SELECT a.id, a.titulo, a.resumo, a.categoria_id, a.faq, a.publicado, a.fixado, a.visualizacoes,"
            + " a.util, a.nao_util, a.update_time,"
            + "<choose><when test=\"q != null\">ts_rank(a.busca, to_tsquery('portuguese', #{q})) AS relevancia,"
            + " ts_headline('portuguese', a.texto, to_tsquery('portuguese', #{q}),"
            + " 'StartSel=' || chr(2) || ', StopSel=' || chr(3) || ', MaxWords=35, MinWords=15, MaxFragments=1') AS trecho</when>"
            + "<otherwise>0 AS relevancia, left(a.texto, 220) AS trecho</otherwise></choose> "
            + FILTRO
            + " ORDER BY a.fixado DESC, relevancia DESC, a.visualizacoes DESC, a.id DESC LIMIT #{limite} OFFSET #{deslocamento}</script>")
    List<KbVOs.ArtigoResumo> buscar(@Param("q") String q, @Param("categorias") Collection<Long> categorias,
                                    @Param("somenteVisiveis") boolean somenteVisiveis, @Param("somenteFaq") boolean somenteFaq,
                                    @Param("faq") Boolean faq, @Param("publicado") Boolean publicado,
                                    @Param("limite") int limite, @Param("deslocamento") int deslocamento);

    @Select("<script>SELECT count(*) " + FILTRO + "</script>")
    long contar(@Param("q") String q, @Param("categorias") Collection<Long> categorias,
                @Param("somenteVisiveis") boolean somenteVisiveis, @Param("somenteFaq") boolean somenteFaq,
                @Param("faq") Boolean faq, @Param("publicado") Boolean publicado);

    @Update("UPDATE itsm.kb_artigo SET visualizacoes = visualizacoes + 1 WHERE id = #{id}")
    void contarVisualizacao(@Param("id") Long id);

    @Update("UPDATE itsm.kb_artigo SET util = (SELECT count(*) FROM itsm.kb_voto v WHERE v.artigo_id = #{id} AND v.util),"
            + " nao_util = (SELECT count(*) FROM itsm.kb_voto v WHERE v.artigo_id = #{id} AND NOT v.util) WHERE id = #{id}")
    void recontarVotos(@Param("id") Long id);

}
