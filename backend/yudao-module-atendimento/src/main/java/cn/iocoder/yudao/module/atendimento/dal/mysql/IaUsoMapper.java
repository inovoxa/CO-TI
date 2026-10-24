package cn.iocoder.yudao.module.atendimento.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.IaUsoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface IaUsoMapper extends BaseMapperX<IaUsoDO> {

    /** Uso dos últimos 30 dias por provedor e finalidade (tela de configuração) */
    @Select("SELECT provedor_id, finalidade, count(*) AS chamadas, count(erro) AS erros, coalesce(sum(tokens_entrada), 0) AS entrada,"
            + " coalesce(sum(tokens_saida), 0) AS saida FROM atendimento.ia_uso WHERE deleted = 0"
            + " AND criado_em >= now() - interval '30 days' GROUP BY provedor_id, finalidade ORDER BY provedor_id, finalidade")
    List<Map<String, Object>> resumo30Dias();

}
