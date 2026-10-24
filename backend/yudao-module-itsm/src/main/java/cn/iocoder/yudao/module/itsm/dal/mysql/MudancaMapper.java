package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.MudancaDO;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.MudancaStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface MudancaMapper extends BaseMapperX<MudancaDO> {

    @Select("SELECT * FROM itsm.mudanca WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MudancaDO selectByIdParaAtualizar(@Param("id") Long id);

    String EXISTE_TECNICO = "EXISTS (SELECT 1 FROM itsm.itil_ator a WHERE a.objeto = 'mudanca' AND a.objeto_id = mudanca.id"
            + " AND a.deleted = 0 AND a.tipo = {0} AND a.user_id = {1})";
    String EXISTE_EQUIPE = "EXISTS (SELECT 1 FROM itsm.itil_ator a WHERE a.objeto = 'mudanca' AND a.objeto_id = mudanca.id"
            + " AND a.deleted = 0 AND a.tipo = {0} AND a.equipe_id IN ({1}))";

    List<Integer> FORA_DA_FILA = List.of(MudancaStatus.APLICADA.getCodigo(), MudancaStatus.FECHADA.getCodigo(),
            MudancaStatus.RECUSADA.getCodigo(), MudancaStatus.CANCELADA.getCodigo());

    /**
     * Fila de mudanças; o escopo por unidade vem do plugin de permissão de dados (dept_id).
     * A fila "agenda" mostra as que têm janela marcada daqui para frente, pela ordem da janela.
     */
    default PageResult<MudancaDO> selectPage(ItilVOs.PaginaReq r, Long userId, Collection<Long> equipes) {
        LambdaQueryWrapperX<MudancaDO> q = new LambdaQueryWrapperX<MudancaDO>()
                .eqIfPresent(MudancaDO::getStatus, r.getStatus())
                .eqIfPresent(MudancaDO::getTipo, r.getTipo())
                .eqIfPresent(MudancaDO::getPrioridade, r.getPrioridade())
                .eqIfPresent(MudancaDO::getCategoriaId, r.getCategoriaId());
        String ordem = "ORDER BY CASE WHEN status IN (5, 6, 13, 14) THEN 1 ELSE 0 END, inicio_previsto ASC NULLS LAST, aberto_em DESC";
        switch (r.getFila() == null ? "abertos" : r.getFila()) {
            case "meus" -> q.notIn(MudancaDO::getStatus, FORA_DA_FILA).apply(EXISTE_TECNICO, AtorTipo.TECNICO.getCodigo(), userId);
            case "minhas-equipes" -> {
                q.notIn(MudancaDO::getStatus, FORA_DA_FILA);
                if (equipes.isEmpty()) {
                    q.apply("1 = 0");
                } else {
                    q.apply(EXISTE_EQUIPE.replace("{1}", String.join(",", equipes.stream().map(String::valueOf).toList())),
                            AtorTipo.TECNICO.getCodigo());
                }
            }
            case "aprovacao" -> q.eq(MudancaDO::getStatus, MudancaStatus.EM_APROVACAO.getCodigo());
            case "agenda" -> {
                q.notIn(MudancaDO::getStatus, List.of(MudancaStatus.FECHADA.getCodigo(), MudancaStatus.RECUSADA.getCodigo(),
                        MudancaStatus.CANCELADA.getCodigo()));
                q.isNotNull(MudancaDO::getInicioPrevisto).ge(MudancaDO::getFimPrevisto, LocalDateTime.now().toLocalDate().atStartOfDay());
                ordem = "ORDER BY inicio_previsto ASC";
            }
            case "encerrados" -> q.in(MudancaDO::getStatus, FORA_DA_FILA);
            case "todos" -> { }
            default -> q.notIn(MudancaDO::getStatus, FORA_DA_FILA);
        }
        ItilVOs.aplicarBusca(q, r.getBusca(), MudancaDO::getId);
        q.last(ordem);
        return selectPage(r, q);
    }

    /** Outras mudanças com janela sobreposta (conflito de agenda), sem as encerradas. */
    default List<MudancaDO> selectSobrepostas(Long id, LocalDateTime inicio, LocalDateTime fim) {
        return selectList(new LambdaQueryWrapperX<MudancaDO>()
                .ne(MudancaDO::getId, id)
                .notIn(MudancaDO::getStatus, List.of(MudancaStatus.FECHADA.getCodigo(), MudancaStatus.RECUSADA.getCodigo(),
                        MudancaStatus.CANCELADA.getCodigo()))
                .lt(MudancaDO::getInicioPrevisto, fim)
                .gt(MudancaDO::getFimPrevisto, inicio)
                .orderByAsc(MudancaDO::getInicioPrevisto)
                .last("LIMIT 20"));
    }

}
