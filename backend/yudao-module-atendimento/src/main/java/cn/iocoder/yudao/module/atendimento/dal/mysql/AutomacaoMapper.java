package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.AutomacaoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AutomacaoMapper extends BaseMapperX<AutomacaoDO> {

    @Update("UPDATE atendimento.automacao SET execucoes = execucoes + 1, ultima_execucao = now() WHERE id = #{id}")
    int registrarExecucao(@Param("id") Long id);

}
