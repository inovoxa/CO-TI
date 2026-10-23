package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChamadoMapper extends BaseMapperX<ChamadoDO> {

    /** Trava a linha até o fim da transação (duas pessoas mexendo no mesmo chamado). */
    @Select("SELECT * FROM itsm.chamado WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ChamadoDO selectByIdParaAtualizar(@Param("id") Long id);

    /**
     * Fila de chamados. O escopo por unidade (perfil do usuário) é aplicado pelo plugin de permissão de dados
     * do yudao sobre a coluna dept_id.
     *
     * @param equipesDoUsuario equipes de quem consulta (fila "minhas-equipes")
     */
    default PageResult<ChamadoDO> selectPage(ChamadoVOs.PaginaReq r, Long userId, Collection<Long> equipesDoUsuario) {
        LambdaQueryWrapperX<ChamadoDO> q = new LambdaQueryWrapperX<ChamadoDO>()
                .eqIfPresent(ChamadoDO::getStatus, r.getStatus())
                .eqIfPresent(ChamadoDO::getTipo, r.getTipo())
                .eqIfPresent(ChamadoDO::getPrioridade, r.getPrioridade())
                .eqIfPresent(ChamadoDO::getCategoriaId, r.getCategoriaId())
                .eqIfPresent(ChamadoDO::getDeptId, r.getDeptId());
        aplicarFila(q, r.getFila(), userId, equipesDoUsuario);
        aplicarBusca(q, r.getBusca());
        if (r.getTecnicoId() != null) {
            q.apply(EXISTE_ATOR_USUARIO, AtorTipo.TECNICO.getCodigo(), r.getTecnicoId());
        }
        if (r.getSolicitanteId() != null) {
            q.apply(EXISTE_ATOR_USUARIO, AtorTipo.SOLICITANTE.getCodigo(), r.getSolicitanteId());
        }
        if (r.getEquipeId() != null) {
            q.apply(EXISTE_ATOR_EQUIPE, AtorTipo.TECNICO.getCodigo(), r.getEquipeId());
        }
        // abertos primeiro, depois pelo prazo (vencidos e mais próximos no topo), depois os mais novos
        q.last("ORDER BY CASE WHEN status < 5 THEN 0 ELSE 1 END, prazo_solucao ASC NULLS LAST, prioridade DESC, aberto_em DESC");
        return selectPage(r, q);
    }

    String EXISTE_ATOR_USUARIO = "EXISTS (SELECT 1 FROM itsm.chamado_ator a WHERE a.chamado_id = chamado.id"
            + " AND a.deleted = 0 AND a.tipo = {0} AND a.user_id = {1})";
    String EXISTE_ATOR_EQUIPE = "EXISTS (SELECT 1 FROM itsm.chamado_ator a WHERE a.chamado_id = chamado.id"
            + " AND a.deleted = 0 AND a.tipo = {0} AND a.equipe_id = {1})";

    private static void aplicarFila(LambdaQueryWrapperX<ChamadoDO> q, String fila, Long userId, Collection<Long> equipes) {
        int solucionado = ChamadoStatus.SOLUCIONADO.getCodigo();
        switch (fila == null ? "abertos" : fila) {
            case "novos" -> q.eq(ChamadoDO::getStatus, ChamadoStatus.NOVO.getCodigo());
            case "meus" -> q.lt(ChamadoDO::getStatus, solucionado)
                    .apply(EXISTE_ATOR_USUARIO, AtorTipo.TECNICO.getCodigo(), userId);
            case "minhas-equipes" -> {
                q.lt(ChamadoDO::getStatus, solucionado);
                if (equipes == null || equipes.isEmpty()) {
                    q.apply("1 = 0");
                } else {
                    q.apply("EXISTS (SELECT 1 FROM itsm.chamado_ator a WHERE a.chamado_id = chamado.id AND a.deleted = 0"
                            + " AND a.tipo = " + AtorTipo.TECNICO.getCodigo() + " AND a.equipe_id IN ("
                            + String.join(",", equipes.stream().map(id -> String.valueOf(id.longValue())).toList()) + "))");
                }
            }
            case "pendentes" -> q.eq(ChamadoDO::getStatus, ChamadoStatus.PENDENTE.getCodigo());
            case "sla" -> q.lt(ChamadoDO::getStatus, solucionado).lt(ChamadoDO::getPrazoSolucao, LocalDateTime.now());
            case "solucionados" -> q.eq(ChamadoDO::getStatus, solucionado);
            case "fechados" -> q.eq(ChamadoDO::getStatus, ChamadoStatus.FECHADO.getCodigo());
            case "todos" -> {
            }
            default -> q.lt(ChamadoDO::getStatus, solucionado);
        }
    }

    private static void aplicarBusca(LambdaQueryWrapperX<ChamadoDO> q, String busca) {
        if (busca == null || busca.isBlank()) {
            return;
        }
        String b = busca.trim();
        String numero = b.startsWith("#") ? b.substring(1) : b;
        if (numero.matches("\\d{1,18}")) {
            q.and(w -> w.eq(ChamadoDO::getId, Long.parseLong(numero)).or().like(ChamadoDO::getTitulo, b));
        } else {
            // ILIKE, sem acento sensível a maiúsculas; o parâmetro vai como bind (sem concatenar no SQL)
            q.apply("titulo ILIKE {0}", "%" + b.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
    }

    /** Contagens para os "chips" da fila, na mesma visão de escopo de dados. */
    @Select("SELECT"
            + " count(*) FILTER (WHERE status < 5) AS abertos,"
            + " count(*) FILTER (WHERE status = 1) AS novos,"
            + " count(*) FILTER (WHERE status = 4) AS pendentes,"
            + " count(*) FILTER (WHERE status < 5 AND prazo_solucao < now()) AS sla,"
            + " count(*) FILTER (WHERE status = 5) AS solucionados"
            + " FROM itsm.chamado WHERE deleted = 0")
    Map<String, Long> selectContagens();

    default List<ChamadoDO> selectListAbertosPorDept(Collection<Long> deptIds) {
        return selectList(new LambdaQueryWrapperX<ChamadoDO>().inIfPresent(ChamadoDO::getDeptId, deptIds)
                .lt(ChamadoDO::getStatus, ChamadoStatus.SOLUCIONADO.getCodigo()));
    }

}
