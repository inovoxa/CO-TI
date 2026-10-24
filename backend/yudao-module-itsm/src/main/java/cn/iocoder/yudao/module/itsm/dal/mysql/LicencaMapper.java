package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.itsm.dal.dataobject.LicencaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LicencaMapper extends BaseMapperX<LicencaDO> {

    /** Trava a licença: duas distribuições ao mesmo tempo não podem passar da quantidade. */
    @Select("SELECT * FROM itsm.licenca WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    LicencaDO selectByIdParaAtualizar(@Param("id") Long id);

}
