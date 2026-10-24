package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ProblemaDO;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.ProblemaStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProblemaMapper extends BaseMapperX<ProblemaDO> {

    @Select("SELECT * FROM itsm.problema WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ProblemaDO selectByIdParaAtualizar(@Param("id") Long id);

    String EXISTE_TECNICO = "EXISTS (SELECT 1 FROM itsm.itil_ator a WHERE a.objeto = 'problema' AND a.objeto_id = problema.id"
            + " AND a.deleted = 0 AND a.tipo = {0} AND a.user_id = {1})";
    String EXISTE_EQUIPE = "EXISTS (SELECT 1 FROM itsm.itil_ator a WHERE a.objeto = 'problema' AND a.objeto_id = problema.id"
            + " AND a.deleted = 0 AND a.tipo = {0} AND a.equipe_id IN ({1}))";

    /** Fila de problemas; o escopo por unidade vem do plugin de permissão de dados (dept_id). */
    default PageResult<ProblemaDO> selectPage(ItilVOs.PaginaReq r, Long userId, Collection<Long> equipes) {
        List<Integer> encerrados = List.of(ProblemaStatus.SOLUCIONADO.getCodigo(), ProblemaStatus.FECHADO.getCodigo());
        LambdaQueryWrapperX<ProblemaDO> q = new LambdaQueryWrapperX<ProblemaDO>()
                .eqIfPresent(ProblemaDO::getStatus, r.getStatus())
                .eqIfPresent(ProblemaDO::getPrioridade, r.getPrioridade())
                .eqIfPresent(ProblemaDO::getCategoriaId, r.getCategoriaId());
        switch (r.getFila() == null ? "abertos" : r.getFila()) {
            case "meus" -> q.notIn(ProblemaDO::getStatus, encerrados).apply(EXISTE_TECNICO, AtorTipo.TECNICO.getCodigo(), userId);
            case "minhas-equipes" -> {
                q.notIn(ProblemaDO::getStatus, encerrados);
                if (equipes.isEmpty()) {
                    q.apply("1 = 0");
                } else {
                    q.apply(EXISTE_EQUIPE.replace("{1}", String.join(",", equipes.stream().map(String::valueOf).toList())),
                            AtorTipo.TECNICO.getCodigo());
                }
            }
            case "encerrados" -> q.in(ProblemaDO::getStatus, encerrados);
            case "todos" -> { }
            default -> q.notIn(ProblemaDO::getStatus, encerrados);
        }
        ItilVOs.aplicarBusca(q, r.getBusca(), ProblemaDO::getId);
        q.last("ORDER BY CASE WHEN status IN (5, 6) THEN 1 ELSE 0 END, prioridade DESC, aberto_em DESC");
        return selectPage(r, q);
    }

}
