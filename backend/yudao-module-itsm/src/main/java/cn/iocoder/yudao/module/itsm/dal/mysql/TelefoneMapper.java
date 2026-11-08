package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.TelefoneVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.TelefoneDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TelefoneMapper extends BaseMapperX<TelefoneDO> {

    /**
     * @param verCelular false: o celular restrito não entra na busca, senão dá para descobrir de quem é um número
     */
    default PageResult<TelefoneDO> pagina(TelefoneVOs.PaginaReq req, boolean verCelular) {
        LambdaQueryWrapperX<TelefoneDO> q = new LambdaQueryWrapperX<>();
        q.eqIfPresent(TelefoneDO::getSetor, req.getSetor());
        q.eqIfPresent(TelefoneDO::getUnidadeId, req.getUnidadeId());
        if (Boolean.TRUE.equals(req.getEmergencia())) {
            q.eq(TelefoneDO::getEmergencia, true);
        }
        String busca = req.getBusca() == null ? "" : req.getBusca().trim();
        if (!busca.isEmpty()) {
            String trecho = "%" + busca.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            // o número é comparado só pelos dígitos: "33011234" acha "(16) 3301-1234"
            String digitos = busca.replaceAll("[^0-9]", "");
            q.and(w -> {
                w.apply("nome ILIKE {0}", trecho).or().apply("setor ILIKE {0}", trecho)
                        .or().apply("unidade_id IN (SELECT u.id FROM monitor_unit u WHERE u.deleted = 0 AND u.name ILIKE {0})", trecho)
                        .or().apply("email ILIKE {0}", trecho).or().apply("ramal ILIKE {0}", trecho).or().apply("telefone ILIKE {0}", trecho);
                if (!digitos.isEmpty()) {
                    w.or().apply("regexp_replace(telefone, '[^0-9]', '', 'g') LIKE {0}", "%" + digitos + "%");
                    String celular = "regexp_replace(celular, '[^0-9]', '', 'g') LIKE {0}";
                    w.or().apply(verCelular ? celular : "celular_publico AND " + celular, "%" + digitos + "%");
                }
            });
        }
        // dentro de uma unidade, quem atende na emergência vem primeiro
        if (req.getUnidadeId() != null) {
            q.orderByDesc(TelefoneDO::getEmergencia);
        }
        q.orderByAsc(TelefoneDO::getNome);
        return selectPage(req, q);
    }

    default List<TelefoneDO> daUnidade(Long unidadeId) {
        return selectList(new LambdaQueryWrapperX<TelefoneDO>().eq(TelefoneDO::getUnidadeId, unidadeId)
                .orderByDesc(TelefoneDO::getEmergencia).orderByAsc(TelefoneDO::getNome).last("LIMIT 30"));
    }

    @Select("SELECT DISTINCT setor FROM itsm.telefone WHERE deleted = 0 AND setor IS NOT NULL ORDER BY setor")
    List<String> setores();

}
