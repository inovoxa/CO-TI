package cn.iocoder.yudao.module.inventario.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.inventario.dal.dataobject.SnmpCredencialDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CmdbSnmpCredencialMapper extends BaseMapperX<SnmpCredencialDO> {
}
