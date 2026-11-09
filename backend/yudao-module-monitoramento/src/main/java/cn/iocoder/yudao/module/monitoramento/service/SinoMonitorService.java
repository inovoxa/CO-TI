package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sino do portal: monitor fora do ar e certificado perto de vencer, para quem enxerga o monitor na tela (permissão de ver
 * monitores e escopo de departamento). Independe dos canais de aviso, e uma falha aqui não atrapalha a verificação nem os canais.
 */
@Slf4j
@Service
public class SinoMonitorService {

    static final String PERMISSAO = "monitoramento:monitor:query";
    static final String MODELO_CAIU = "coti_monitor_caiu";
    static final String MODELO_CERTIFICADO = "coti_monitor_certificado";
    private static final int MAX_MOTIVO = 120;

    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private NotifySendService notifySendService;
    @Resource
    private WebSocketMessageSender webSocketMessageSender;

    @DataPermission(enable = false)
    public void caiu(MonitorDO m, String unidade, String link) {
        String motivo = Optional.ofNullable(m.getUltimaMsg()).filter(s -> !s.isBlank()).orElse("sem detalhes");
        avisar(m, MODELO_CAIU, unidade, link, "motivo", motivo.length() > MAX_MOTIVO ? motivo.substring(0, MAX_MOTIVO - 1) + "…" : motivo);
    }

    @DataPermission(enable = false)
    public void certificado(MonitorDO m, String unidade, String link) {
        avisar(m, MODELO_CERTIFICADO, unidade, link, "prazo", prazo(m.getCertDias()));
    }

    private void avisar(MonitorDO m, String modelo, String unidade, String link, String campo, String valor) {
        Map<String, Object> params = new HashMap<>();
        params.put("monitor", unidade == null ? m.getNome() : m.getNome() + " · " + unidade);
        params.put(campo, valor);
        params.put("link", link);
        try {
            destinatarios(m.getDeptId()).forEach(id -> enviar(id, modelo, params, m));
        } catch (RuntimeException e) {
            log.error("[monitoramento] sem aviso no sino para o monitor {}", m.getId(), e);
        }
    }

    private void enviar(Long userId, String modelo, Map<String, Object> params, MonitorDO m) {
        try {
            notifySendService.sendSingleNotifyToAdmin(userId, modelo, params);
            webSocketMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(), userId, "sino", Map.of("monitor", m.getId()));
        } catch (RuntimeException e) {
            // uma pessoa com problema não pode deixar as demais sem aviso; o sino também consulta de tempos em tempos
            log.warn("[monitoramento] aviso no sino para {} (monitor {}): {}", userId, m.getId(), e.getMessage());
        }
    }

    private List<Long> destinatarios(Long deptId) {
        Set<Long> ids = new HashSet<>(permissionService.getUserIdListByPermission(PERMISSAO));
        // o administrador tem acesso a tudo sem que a permissão esteja gravada no perfil dele
        RoleDO admin = roleMapper.selectByCode(RoleCodeEnum.SUPER_ADMIN.getCode());
        if (admin != null) {
            ids.addAll(permissionService.getUserRoleIdListByRoleId(Set.of(admin.getId())));
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return adminUserMapper.selectByIds(ids).stream().filter(u -> CommonStatusEnum.isEnable(u.getStatus()))
                .filter(u -> enxerga(permissionService.getDeptDataPermission(u.getId()), deptId)).map(AdminUserDO::getId).toList();
    }

    /** Mesma regra da tela: quem não tem acesso a todos só vê monitor de departamento do seu escopo. */
    static boolean enxerga(DeptDataPermissionRespDTO escopo, Long deptId) {
        return Boolean.TRUE.equals(escopo.getAll()) || (deptId != null && escopo.getDeptIds().contains(deptId));
    }

    /** Negativo só acontece com "ignorar erros de TLS": sem essa opção, certificado vencido já derruba o monitor. */
    static String prazo(int dias) {
        if (dias < 0) {
            return "venceu há " + -dias + (dias == -1 ? " dia" : " dias");
        }
        if (dias == 0) {
            return "vence hoje";
        }
        return "vence em " + dias + (dias == 1 ? " dia" : " dias");
    }

}
