package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EstoqueItemDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EstoqueMovimentoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EstoqueTipoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public final class EstoqueMappers {

    private EstoqueMappers() {
    }

    @Mapper
    public interface Tipo extends BaseMapperX<EstoqueTipoDO> {
    }

    @Mapper
    public interface Item extends BaseMapperX<EstoqueItemDO> {
        /** Trava a linha até o fim da transação: duas retiradas ao mesmo tempo não passam do saldo. */
        @Select("SELECT * FROM itsm.estoque_item WHERE id = #{id} AND deleted = 0 FOR UPDATE")
        EstoqueItemDO selectByIdParaAtualizar(@Param("id") Long id);
    }

    @Mapper
    public interface Movimento extends BaseMapperX<EstoqueMovimentoDO> {
    }

}
