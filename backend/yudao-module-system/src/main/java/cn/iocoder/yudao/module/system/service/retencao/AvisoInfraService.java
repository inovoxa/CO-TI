package cn.iocoder.yudao.module.system.service.retencao;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** Avisa os administradores no sino, no máximo uma vez por dia para cada assunto. */
@Slf4j
@Service
public class AvisoInfraService {

    private static final String PERFIL = "super_admin";

    @Resource
    private ExecucaoUnica execucaoUnica;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private PermissionService permissionService;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private NotifySendService notifySendService;

    /** @param assunto identifica o aviso: o mesmo assunto só avisa de novo no dia seguinte */
    public void avisar(String assunto, String texto) {
        execucaoUnica.executar("infra:aviso:" + assunto + ":" + LocalDate.now(), Duration.ofDays(1), () -> enviar(texto));
    }

    private void enviar(String texto) {
        RoleDO perfil = roleMapper.selectByCode(PERFIL);
        if (perfil == null) {
            log.warn("[enviar][perfil {} não existe; aviso só no log: {}]", PERFIL, texto);
            return;
        }
        Set<Long> ids = permissionService.getUserRoleIdListByRoleId(Set.of(perfil.getId()));
        if (ids.isEmpty()) {
            return;
        }
        for (AdminUserDO u : adminUserMapper.selectByIds(ids)) {
            if (CommonStatusEnum.isEnable(u.getStatus())) {
                notifySendService.sendSingleNotifyToAdmin(u.getId(), "coti_infra_aviso", Map.of("texto", texto, "link", "/infra/geral"));
            }
        }
    }

}
