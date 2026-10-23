package cn.iocoder.yudao.module.identidade.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.identidade.dal.dataobject.MfaTotpDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MfaTotpMapper extends BaseMapperX<MfaTotpDO> {

    default MfaTotpDO selectByUserId(Long userId) {
        return selectOne(MfaTotpDO::getUserId, userId);
    }

    /** Trava a linha até o fim da transação: dois logins com o mesmo código não passam juntos. */
    @Select("SELECT * FROM identidade.mfa_totp WHERE user_id = #{userId} AND deleted = 0 FOR UPDATE")
    MfaTotpDO selectByUserIdParaAtualizar(@Param("userId") Long userId);

}
