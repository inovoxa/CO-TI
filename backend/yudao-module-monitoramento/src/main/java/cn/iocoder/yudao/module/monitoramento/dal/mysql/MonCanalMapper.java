package cn.iocoder.yudao.module.monitoramento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.CanalDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonCanalMapper extends BaseMapperX<CanalDO> {
}
