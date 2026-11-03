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

    default PageResult<TelefoneDO> pagina(TelefoneVOs.PaginaReq req) {
        LambdaQueryWrapperX<TelefoneDO> q = new LambdaQueryWrapperX<>();
        q.eqIfPresent(TelefoneDO::getSetor, req.getSetor());
        String busca = req.getBusca() == null ? "" : req.getBusca().trim();
        if (!busca.isEmpty()) {
            String trecho = "%" + busca.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            // o número é comparado só pelos dígitos: "33011234" acha "(16) 3301-1234"
            String digitos = busca.replaceAll("[^0-9]", "");
            q.and(w -> {
                w.apply("nome ILIKE {0}", trecho).or().apply("setor ILIKE {0}", trecho).or().apply("unidade ILIKE {0}", trecho)
                        .or().apply("email ILIKE {0}", trecho).or().apply("ramal ILIKE {0}", trecho).or().apply("telefone ILIKE {0}", trecho);
                if (!digitos.isEmpty()) {
                    w.or().apply("regexp_replace(telefone, '[^0-9]', '', 'g') LIKE {0}", "%" + digitos + "%");
                }
            });
        }
        q.orderByAsc(TelefoneDO::getNome);
        return selectPage(req, q);
    }

    @Select("SELECT DISTINCT setor FROM itsm.telefone WHERE deleted = 0 AND setor IS NOT NULL ORDER BY setor")
    List<String> setores();

}
