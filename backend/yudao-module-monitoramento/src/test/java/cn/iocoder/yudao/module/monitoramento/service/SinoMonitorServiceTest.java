package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SinoMonitorServiceTest {

    private static final Long ADMIN = 1L, NOC = 2L, GESTOR_DA_UNIDADE = 3L, GESTOR_DE_OUTRA = 4L, DESATIVADO = 5L;
    private static final Long DEPT = 100L;
    private static final String LINK = "/monitoramento/monitores?id=7";

    @InjectMocks
    private SinoMonitorService service;
    @Mock
    private PermissionService permissionService;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private NotifySendService notifySendService;
    @Mock
    private WebSocketMessageSender webSocketMessageSender;

    @BeforeEach
    void cenario() {
        when(permissionService.getUserIdListByPermission(SinoMonitorService.PERMISSAO))
                .thenReturn(new HashSet<>(Arrays.asList(NOC, GESTOR_DA_UNIDADE, GESTOR_DE_OUTRA, DESATIVADO)));
        RoleDO admin = new RoleDO();
        admin.setId(50L);
        when(roleMapper.selectByCode("super_admin")).thenReturn(admin);
        when(permissionService.getUserRoleIdListByRoleId(Set.of(50L))).thenReturn(Set.of(ADMIN));
        when(adminUserMapper.selectByIds(anyCollection())).thenReturn(List.of(usuario(ADMIN, true), usuario(NOC, true),
                usuario(GESTOR_DA_UNIDADE, true), usuario(GESTOR_DE_OUTRA, true), usuario(DESATIVADO, false)));
        when(permissionService.getDeptDataPermission(ADMIN)).thenReturn(escopo(true));
        when(permissionService.getDeptDataPermission(NOC)).thenReturn(escopo(true));
        when(permissionService.getDeptDataPermission(GESTOR_DA_UNIDADE)).thenReturn(escopo(false, DEPT));
        when(permissionService.getDeptDataPermission(GESTOR_DE_OUTRA)).thenReturn(escopo(false, 200L));
        when(permissionService.getDeptDataPermission(DESATIVADO)).thenReturn(escopo(true));
    }

    @Test
    void avisaSoQuemEnxergaOMonitorEIncluiOAdministrador() {
        service.caiu(monitor(DEPT), "Centro", LINK);

        verify(notifySendService).sendSingleNotifyToAdmin(eq(ADMIN), eq(SinoMonitorService.MODELO_CAIU), anyMap());
        verify(notifySendService).sendSingleNotifyToAdmin(eq(NOC), eq(SinoMonitorService.MODELO_CAIU), anyMap());
        verify(notifySendService).sendSingleNotifyToAdmin(eq(GESTOR_DA_UNIDADE), eq(SinoMonitorService.MODELO_CAIU), anyMap());
        verify(notifySendService, never()).sendSingleNotifyToAdmin(eq(GESTOR_DE_OUTRA), any(), anyMap());
        verify(notifySendService, never()).sendSingleNotifyToAdmin(eq(DESATIVADO), any(), anyMap());
    }

    @Test
    void monitorSemDepartamentoSoParaQuemVeTudo() {
        service.caiu(monitor(null), null, LINK);

        verify(notifySendService, times(2)).sendSingleNotifyToAdmin(anyLong(), any(), anyMap());
        verify(notifySendService, never()).sendSingleNotifyToAdmin(eq(GESTOR_DA_UNIDADE), any(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void levaTodosOsParametrosDoModelo() {
        MonitorDO m = monitor(DEPT);
        m.setUltimaMsg("x".repeat(500));
        service.caiu(m, "Centro", LINK);

        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(notifySendService).sendSingleNotifyToAdmin(eq(ADMIN), any(), params.capture());
        assertEquals("Fila · Centro", params.getValue().get("monitor"));
        assertEquals(LINK, params.getValue().get("link"));
        assertEquals(120, ((String) params.getValue().get("motivo")).length());
    }

    @Test
    @SuppressWarnings("unchecked")
    void certificadoInformaOPrazo() {
        MonitorDO m = monitor(DEPT);
        m.setCertDias(5);
        service.certificado(m, null, LINK);

        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(notifySendService).sendSingleNotifyToAdmin(eq(NOC), eq(SinoMonitorService.MODELO_CERTIFICADO), params.capture());
        assertEquals("vence em 5 dias", params.getValue().get("prazo"));
        assertEquals("Fila", params.getValue().get("monitor"));
    }

    @Test
    void falhaParaUmaPessoaNaoDeixaAsOutrasSemAviso() {
        doThrow(new IllegalStateException("falhou")).when(notifySendService).sendSingleNotifyToAdmin(eq(ADMIN), any(), anyMap());

        assertDoesNotThrow(() -> service.caiu(monitor(DEPT), null, LINK));

        verify(notifySendService).sendSingleNotifyToAdmin(eq(NOC), any(), anyMap());
        verify(notifySendService).sendSingleNotifyToAdmin(eq(GESTOR_DA_UNIDADE), any(), anyMap());
    }

    @Test
    void falhaAoBuscarDestinatariosNaoInterrompeAVerificacao() {
        when(permissionService.getUserIdListByPermission(any())).thenThrow(new IllegalStateException("banco fora"));

        assertDoesNotThrow(() -> service.caiu(monitor(DEPT), null, LINK));

        verifyNoInteractions(notifySendService);
    }

    @Test
    void prazoEmTexto() {
        assertEquals("vence em 14 dias", SinoMonitorService.prazo(14));
        assertEquals("vence em 1 dia", SinoMonitorService.prazo(1));
        assertEquals("vence hoje", SinoMonitorService.prazo(0));
        assertEquals("venceu há 1 dia", SinoMonitorService.prazo(-1));
        assertEquals("venceu há 3 dias", SinoMonitorService.prazo(-3));
    }

    private static MonitorDO monitor(Long dept) {
        return MonitorDO.builder().id(7L).nome("Fila").deptId(dept).ultimaMsg("código 503").build();
    }

    private static AdminUserDO usuario(Long id, boolean ativo) {
        AdminUserDO u = new AdminUserDO();
        u.setId(id);
        u.setStatus(ativo ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus());
        return u;
    }

    private static DeptDataPermissionRespDTO escopo(boolean todos, Long... depts) {
        DeptDataPermissionRespDTO d = new DeptDataPermissionRespDTO();
        d.setAll(todos);
        d.getDeptIds().addAll(Arrays.asList(depts));
        return d;
    }

}
