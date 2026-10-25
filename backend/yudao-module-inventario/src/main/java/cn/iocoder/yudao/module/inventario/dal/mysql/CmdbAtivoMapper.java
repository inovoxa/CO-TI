package cn.iocoder.yudao.module.inventario.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CmdbAtivoMapper extends BaseMapperX<AtivoDO> {

    @Select("SELECT * FROM cmdb.ativo WHERE device_id = #{deviceId} AND deleted = 0 FOR UPDATE")
    AtivoDO selectPorDeviceParaAtualizar(@Param("deviceId") String deviceId);

    /** Ativo com uma placa de rede com este MAC (agente reinstalado com outro DEVICEID). */
    @Select("SELECT a.* FROM cmdb.ativo a WHERE a.deleted = 0 AND (upper(a.mac) = upper(#{mac}) OR EXISTS (SELECT 1 FROM cmdb.ativo_componente c"
            + " WHERE c.ativo_id = a.id AND c.deleted = 0 AND c.tipo = 'rede' AND upper(c.chave) LIKE upper(#{mac}) || '%')) ORDER BY a.id LIMIT 1")
    AtivoDO selectPorMac(@Param("mac") String mac);

    /** Quantos ativos de cada software (catálogo). */
    @Select({"<script>SELECT s.id, s.nome, s.editora, count(DISTINCT x.ativo_id) AS instalacoes, count(DISTINCT x.versao) AS versoes",
            " FROM cmdb.software s JOIN cmdb.ativo_software x ON x.software_id = s.id AND x.deleted = 0",
            " JOIN cmdb.ativo a ON a.id = x.ativo_id AND a.deleted = 0",
            " WHERE s.deleted = 0<if test='busca != null'> AND (s.nome ILIKE #{busca} OR s.editora ILIKE #{busca})</if>",
            " GROUP BY s.id, s.nome, s.editora ORDER BY instalacoes DESC, s.nome LIMIT #{limite} OFFSET #{deslocamento}</script>"})
    List<Map<String, Object>> softwares(@Param("busca") String busca, @Param("limite") int limite, @Param("deslocamento") int deslocamento);

    @Select({"<script>SELECT count(DISTINCT s.id) FROM cmdb.software s JOIN cmdb.ativo_software x ON x.software_id = s.id AND x.deleted = 0",
            " JOIN cmdb.ativo a ON a.id = x.ativo_id AND a.deleted = 0",
            " WHERE s.deleted = 0<if test='busca != null'> AND (s.nome ILIKE #{busca} OR s.editora ILIKE #{busca})</if></script>"})
    long contarSoftwares(@Param("busca") String busca);

    /** Versões de agente em uso (página "Agentes"). */
    @Select("SELECT a.origem, coalesce(a.agente, '?') AS agente, count(*) AS total FROM cmdb.ativo a WHERE a.deleted = 0"
            + " AND a.origem <> 'manual' GROUP BY a.origem, coalesce(a.agente, '?') ORDER BY total DESC LIMIT 30")
    List<Map<String, Object>> versoesAgente();

}
