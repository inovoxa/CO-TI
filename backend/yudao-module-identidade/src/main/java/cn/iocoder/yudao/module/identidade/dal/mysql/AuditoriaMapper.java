package cn.iocoder.yudao.module.identidade.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.controller.admin.vo.AuditoriaPageReqVO;
import cn.iocoder.yudao.module.identidade.dal.dataobject.AuditoriaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditoriaMapper extends BaseMapperX<AuditoriaDO> {

    /** Serializa as inserções da cadeia até o fim da transação (o número é só a chave do lock). */
    @Select("SELECT pg_advisory_xact_lock(727001)::text")
    String travarCadeia();

    @Select("SELECT nextval('identidade.auditoria_seq')")
    Long proximoId();

    @Select("SELECT * FROM identidade.auditoria ORDER BY id DESC LIMIT 1")
    AuditoriaDO selectUltimo();

    @Select("SELECT * FROM identidade.auditoria WHERE id > #{aposId} ORDER BY id LIMIT #{limite}")
    List<AuditoriaDO> selectLote(@Param("aposId") long aposId, @Param("limite") int limite);

    default PageResult<AuditoriaDO> selectPage(AuditoriaPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<AuditoriaDO>()
                .eqIfPresent(AuditoriaDO::getAcao, req.getAcao())
                .likeIfPresent(AuditoriaDO::getUsuario, req.getUsuario())
                .eqIfPresent(AuditoriaDO::getUserId, req.getUserId())
                .betweenIfPresent(AuditoriaDO::getMomento, req.getMomento())
                .orderByDesc(AuditoriaDO::getId));
    }

}
