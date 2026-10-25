package cn.iocoder.yudao.module.inventario.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.inventario.dal.dataobject.LicencaSoftwareDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CmdbLicencaSoftwareMapper extends BaseMapperX<LicencaSoftwareDO> {

    /** Máquinas (não baixadas) com algum software da licença, por licença. */
    @Select("SELECT ls.licenca_id, count(DISTINCT a.id) AS instalacoes FROM cmdb.licenca_software ls"
            + " JOIN cmdb.ativo_software x ON x.software_id = ls.software_id AND x.deleted = 0"
            + " JOIN cmdb.ativo a ON a.id = x.ativo_id AND a.deleted = 0 AND a.situacao <> 'baixado'"
            + " WHERE ls.deleted = 0 GROUP BY ls.licenca_id")
    List<Map<String, Object>> instalacoesPorLicenca();

    @Select("SELECT DISTINCT a.id FROM cmdb.licenca_software ls"
            + " JOIN cmdb.ativo_software x ON x.software_id = ls.software_id AND x.deleted = 0"
            + " JOIN cmdb.ativo a ON a.id = x.ativo_id AND a.deleted = 0 AND a.situacao <> 'baixado'"
            + " WHERE ls.deleted = 0 AND ls.licenca_id = #{licencaId}")
    List<Long> ativosDaLicenca(@Param("licencaId") Long licencaId);

}
