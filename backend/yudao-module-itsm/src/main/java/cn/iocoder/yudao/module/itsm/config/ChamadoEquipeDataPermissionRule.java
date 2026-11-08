package cn.iocoder.yudao.module.itsm.config;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRule;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EquipeMembroDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.EquipeMembroMapper;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import jakarta.annotation.Resource;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quem não tem {@link #P_VER_TODOS} só enxerga, na equipe técnica, os chamados atribuídos a si, a uma equipe sua ou
 * registrados por si. Fica na regra de dados para valer em tudo o que lê chamados: fila, contagens, painel, Excel, detalhe
 * e assistente. Os fluxos de participante (Meus chamados) desligam o escopo e seguem iguais.
 */
@Component
public class ChamadoEquipeDataPermissionRule implements DataPermissionRule {

    public static final String P_VER_TODOS = "itsm:chamado:ver-todos";
    private static final String CONTEXTO = "itsmChamadoEquipes";

    // preguiçosos: esta regra entra no interceptor do MyBatis, de que os mappers dependem
    @Resource
    @Lazy
    private PermissionCommonApi permissionApi;
    @Resource
    @Lazy
    private EquipeMembroMapper equipeMembroMapper;

    @Override
    public Set<String> getTableNames() {
        return Set.of("chamado");
    }

    @Override
    public Expression getExpression(String tableName, Alias tableAlias) {
        LoginUser u = SecurityFrameworkUtils.getLoginUser();
        if (u == null || !UserTypeEnum.ADMIN.getValue().equals(u.getUserType())) {
            return null;
        }
        // a mesma requisição consulta várias vezes: guarda a resposta; "todos" é a lista vazia com a marca abaixo
        String sql = u.getContext(CONTEXTO, String.class);
        String tabela = tableAlias != null ? tableAlias.getName() : tableName;
        if (sql == null) {
            sql = permissionApi.hasAnyPermissions(u.getId(), P_VER_TODOS) ? TODOS : modelo(u.getId(), equipes(u.getId()));
            u.setContext(CONTEXTO, sql);
        }
        if (TODOS.equals(sql)) {
            return null;
        }
        try {
            return CCJSqlParserUtil.parseCondExpression(sql.replace(TABELA, tabela));
        } catch (JSQLParserException e) {
            throw new IllegalStateException("Condição de equipes inválida", e);
        }
    }

    private static final String TODOS = "*";
    private static final String TABELA = "#t#";

    private List<Long> equipes(Long userId) {
        return equipeMembroMapper.selectList(EquipeMembroDO::getUserId, userId).stream().map(EquipeMembroDO::getEquipeId).toList();
    }

    /** Só números entram no texto. */
    static String modelo(Long userId, Collection<Long> equipes) {
        String atores = "a.user_id = " + userId.longValue();
        if (!equipes.isEmpty()) {
            atores += " OR a.equipe_id IN (" + equipes.stream().map(id -> String.valueOf(id.longValue()))
                    .collect(Collectors.joining(",")) + ")";
        }
        return "(" + TABELA + ".registrado_por = " + userId.longValue() + " OR EXISTS (SELECT 1 FROM itsm.chamado_ator a"
                + " WHERE a.chamado_id = " + TABELA + ".id AND a.deleted = 0 AND a.tipo = " + AtorTipo.TECNICO.getCodigo()
                + " AND (" + atores + ")))";
    }

}
