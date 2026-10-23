package cn.iocoder.yudao.module.identidade.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.identidade.dal.dataobject.VinculoAdDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VinculoAdMapper extends BaseMapperX<VinculoAdDO> {

    default VinculoAdDO selectByGuid(String guid) {
        return selectOne(VinculoAdDO::getGuid, guid);
    }

    default VinculoAdDO selectByUserId(Long userId) {
        return selectOne(VinculoAdDO::getUserId, userId);
    }

    /** Vínculos que a última sincronização completa não encontrou mais no AD. */
    default List<VinculoAdDO> selectListNaoVistosDesde(LocalDateTime inicio) {
        return selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<VinculoAdDO>()
                .lt(VinculoAdDO::getUltimaSincronizacao, inicio));
    }

}
