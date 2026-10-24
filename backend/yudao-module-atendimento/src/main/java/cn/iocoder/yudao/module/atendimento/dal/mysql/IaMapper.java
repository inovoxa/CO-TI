package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.module.atendimento.dal.dataobject.IaConfigDO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Configuração da IA e as consultas que o assistente e o copiloto fazem na base de conhecimento do Service Desk.
 */
@Mapper
public interface IaMapper extends BaseMapperX<IaConfigDO> {

    /**
     * Artigos publicados e dentro da validade, pela relevância.
     *
     * @param soFaq só os marcados como FAQ (os que qualquer pessoa vê no portal): é o que o assistente usa com o contato
     * @param q     consulta para to_tsquery('portuguese', ...) (BuscaTexto)
     */
    @Select({"<script>SELECT a.id, a.titulo, left(a.texto, #{tamanho}) AS texto FROM itsm.kb_artigo a",
            " WHERE a.deleted = 0 AND a.publicado = true<if test='soFaq'> AND a.faq = true</if>",
            " AND (a.inicio_publicacao IS NULL OR a.inicio_publicacao &lt;= now()) AND (a.fim_publicacao IS NULL OR a.fim_publicacao &gt; now())",
            " AND a.busca @@ to_tsquery('portuguese', #{q})",
            " ORDER BY a.fixado DESC, ts_rank(a.busca, to_tsquery('portuguese', #{q})) DESC LIMIT #{limite}</script>"})
    List<Map<String, Object>> artigos(@Param("q") String q, @Param("soFaq") boolean soFaq, @Param("limite") int limite,
                                      @Param("tamanho") int tamanho);

}
