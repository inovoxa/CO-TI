package cn.iocoder.yudao.module.inventario.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.inventario.dal.dataobject.PacoteDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CmdbPacoteMapper extends BaseMapperX<PacoteDO> {
}
