package cn.iocoder.yudao.module.monitoramento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.ManutencaoDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonManutencaoMapper extends BaseMapperX<ManutencaoDO> {
}
