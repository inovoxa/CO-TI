package cn.iocoder.yudao.module.acesso.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.acesso.dal.dataobject.AcessoLinkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AcessoLinkMapper extends BaseMapperX<AcessoLinkDO> {

    /** Enfileira até o fim da transação: dois cliques ao mesmo tempo não passam do limite de links da pessoa. */
    @Select("SELECT pg_advisory_xact_lock(#{chave})::text")
    String travar(@Param("chave") long chave);

}
