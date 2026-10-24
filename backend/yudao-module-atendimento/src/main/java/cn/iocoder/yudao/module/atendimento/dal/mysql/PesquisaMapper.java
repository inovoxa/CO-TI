package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.PesquisaDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PesquisaMapper extends BaseMapperX<PesquisaDO> {
}
