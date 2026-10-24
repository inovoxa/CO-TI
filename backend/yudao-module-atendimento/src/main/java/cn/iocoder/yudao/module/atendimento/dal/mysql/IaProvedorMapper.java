package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.IaProvedorDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IaProvedorMapper extends BaseMapperX<IaProvedorDO> {

    default List<IaProvedorDO> emOrdem() {
        return selectList(new LambdaQueryWrapperX<IaProvedorDO>().orderByAsc(IaProvedorDO::getPrioridade).orderByAsc(IaProvedorDO::getId));
    }

}
