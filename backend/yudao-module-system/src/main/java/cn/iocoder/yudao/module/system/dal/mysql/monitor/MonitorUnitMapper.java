package cn.iocoder.yudao.module.system.dal.mysql.monitor;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MonitorUnitMapper extends BaseMapperX<MonitorUnitDO> {

    default List<MonitorUnitDO> selectActiveList() {
        return selectList(MonitorUnitDO::getStatus, CommonStatusEnum.ENABLE.getStatus());
    }

}
