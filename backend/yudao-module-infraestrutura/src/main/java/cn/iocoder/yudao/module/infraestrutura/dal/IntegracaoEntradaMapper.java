package cn.iocoder.yudao.module.infraestrutura.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IntegracaoEntradaMapper extends BaseMapperX<IntegracaoEntradaDO> {

    default IntegracaoEntradaDO selectByCodigo(String codigo) {
        return selectOne(IntegracaoEntradaDO::getCodigo, codigo);
    }

}
