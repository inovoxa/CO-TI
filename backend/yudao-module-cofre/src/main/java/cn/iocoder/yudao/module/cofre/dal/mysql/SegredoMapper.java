package cn.iocoder.yudao.module.cofre.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cofre.dal.dataobject.SegredoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SegredoMapper extends BaseMapperX<SegredoDO> {

    @Select("SELECT nextval('cofre.segredo_seq')")
    Long proximoId();

}
