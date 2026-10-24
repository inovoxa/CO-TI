package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaAgenteDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.CaixaAgenteMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.CONVERSA_SEM_ACESSO;

/**
 * Quem vê o quê no atendimento: cada pessoa vê as conversas das caixas de que participa; quem tem
 * {@code atendimento:conversa:todas} (supervisão) vê todas. O escopo por unidade do yudao não se aplica aqui: o
 * recorte é a caixa de entrada.
 */
@Component
public class Acesso {

    public static final String P_QUERY = "atendimento:conversa:query";
    public static final String P_TODAS = "atendimento:conversa:todas";
    public static final String P_ATRIBUIR = "atendimento:conversa:atribuir";
    /** Menu do botão "Ver todas as caixas" (V24) */
    private static final long MENU_TODAS = 52012L;

    @Resource
    private CaixaAgenteMapper caixaAgenteMapper;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleMapper roleMapper;

    private volatile Set<Long> supervisores = Set.of();
    private volatile long supervisoresEm;

    public boolean veTodas(Long userId) {
        return permissionApi.hasAnyPermissions(userId, P_TODAS);
    }

    public boolean pode(Long userId, String permissao) {
        return permissionApi.hasAnyPermissions(userId, permissao);
    }

    public Set<Long> caixasDe(Long userId) {
        return caixaAgenteMapper.selectList(CaixaAgenteDO::getUserId, userId).stream()
                .map(CaixaAgenteDO::getCaixaId).collect(Collectors.toSet());
    }

    public Set<Long> membros(Long caixaId) {
        return caixaAgenteMapper.selectList(CaixaAgenteDO::getCaixaId, caixaId).stream()
                .map(CaixaAgenteDO::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean membro(Long caixaId, Long userId) {
        return caixaAgenteMapper.selectCount(new LambdaQueryWrapperX<CaixaAgenteDO>()
                .eq(CaixaAgenteDO::getCaixaId, caixaId).eq(CaixaAgenteDO::getUserId, userId)) > 0;
    }

    public void exigir(ConversaDO c, Long userId) {
        if (!veTodas(userId) && !membro(c.getCaixaId(), userId)) {
            throw exception(CONVERSA_SEM_ACESSO);
        }
    }

    /** Quem recebe os avisos em tempo real de uma caixa: os membros e a supervisão. */
    public Set<Long> interessados(Long caixaId, Long agenteId) {
        Set<Long> out = new LinkedHashSet<>(membros(caixaId));
        out.addAll(supervisores());
        if (agenteId != null) {
            out.add(agenteId);
        }
        return out;
    }

    /** Pessoas com "ver todas as caixas" ou administrador; guardado por 1 minuto. */
    private Set<Long> supervisores() {
        long agora = System.currentTimeMillis();
        if (agora - supervisoresEm > 60_000) {
            Set<Long> papeis = new HashSet<>(permissionService.getMenuRoleIdListByMenuIdFromCache(MENU_TODAS));
            roleMapper.selectList().stream().filter(r -> RoleCodeEnum.isSuperAdmin(r.getCode())).map(RoleDO::getId).forEach(papeis::add);
            supervisores = papeis.isEmpty() ? Set.of() : Set.copyOf(permissionApi.getUserRoleIdListByRoleIds(papeis));
            supervisoresEm = agora;
        }
        return supervisores;
    }

}
