package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.itsm.dal.mysql.TelefoneMapper;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.UnidadeVOs;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonManutencaoMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import cn.iocoder.yudao.module.itsm.dal.dataobject.TelefoneDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.ManutencaoDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.UNIDADE_NOME_REPETIDO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Nome repetido não entra, e juntar leva tudo da repetida para a que fica. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnidadeServiceTest {

    @Mock
    private MonitorUnitMapper monitorUnitMapper;
    @Mock
    private MonMonitorMapper monMonitorMapper;
    @Mock
    private MonManutencaoMapper monManutencaoMapper;
    @Mock
    private TelefoneMapper telefoneMapper;
    @Mock
    private DeptApi deptApi;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private UnidadeService service;

    /** Sem o Spring, o MyBatis-Plus precisa conhecer as tabelas para montar os filtros por lambda. */
    @BeforeAll
    static void tabelas() {
        MapperBuilderAssistant m = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(MonitorUnitDO.class, MonitorDO.class, ManutencaoDO.class, TelefoneDO.class)) {
            TableInfoHelper.initTableInfo(m, c);
        }
    }

    @BeforeEach
    void preparar() {
        when(deptApi.getDept(anyLong())).thenReturn(new DeptRespDTO());
        when(monitorUnitMapper.selectList()).thenReturn(List.of(unit(1L, "São Carlos"), unit(2L, "UBS Centro")));
        when(monitorUnitMapper.selectById(1L)).thenReturn(unit(1L, "São Carlos"));
        when(monitorUnitMapper.selectById(2L)).thenReturn(unit(2L, "UBS Centro"));
    }

    @Test
    void comparaSemAcentoMaiusculasNemEspacos() {
        assertEquals("sao carlos", UnidadeService.chaveNome("  SÃO   Carlos "));
        assertEquals(UnidadeService.chaveNome("Educação"), UnidadeService.chaveNome("educacao"));
    }

    @Test
    void naoCadastraNomeQueJaExiste() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.salvar(req(null, "sao  carlos")));
        assertEquals(UNIDADE_NOME_REPETIDO.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("São Carlos"));
        verify(monitorUnitMapper, never()).insert(any(MonitorUnitDO.class));
    }

    @Test
    void naoRenomeiaParaNomeDeOutra() {
        assertThrows(ServiceException.class, () -> service.salvar(req(2L, "SÃO CARLOS")));
        verify(monitorUnitMapper, never()).updateById(any(MonitorUnitDO.class));
    }

    @Test
    void repetidaQueJaExistiaContinuaEditavelSemTrocarONome() {
        when(monitorUnitMapper.selectList()).thenReturn(List.of(unit(1L, "São Carlos"), unit(3L, "Sao Carlos")));
        when(monitorUnitMapper.selectById(3L)).thenReturn(unit(3L, "Sao Carlos"));

        service.salvar(req(3L, "Sao Carlos"));

        verify(monitorUnitMapper).updateById(any(MonitorUnitDO.class));
    }

    @Test
    void cadastraNomeNovo() {
        service.salvar(req(null, "UBS Vila Xavier"));
        verify(monitorUnitMapper).insert(any(MonitorUnitDO.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void juntarLevaTudoParaAQueFicaEExcluiARepetida() {
        when(monMonitorMapper.update(isNull(), any())).thenReturn(3);
        when(monManutencaoMapper.update(isNull(), any())).thenReturn(1);
        when(telefoneMapper.update(isNull(), any())).thenReturn(2);

        UnidadeVOs.JuntarResp r = service.juntar(2L, 1L, 99L);

        assertEquals(3, r.getMonitores());
        assertEquals(1, r.getManutencoes());
        assertEquals(2, r.getTelefones());
        verify(monitorUnitMapper).deleteById(2L);
        verify(monitorUnitMapper, never()).deleteById(1L);
        ArgumentCaptor<Map<String, Object>> detalhes = ArgumentCaptor.forClass(Map.class);
        verify(auditoriaService).registrar(eq("monitoramento.juntar_unidades"), eq(99L), isNull(), detalhes.capture());
        assertEquals("UBS Centro (#2)", detalhes.getValue().get("excluida"));
        assertEquals("São Carlos (#1)", detalhes.getValue().get("mantida"));
    }

    @Test
    void naoJuntaComElaMesma() {
        assertThrows(ServiceException.class, () -> service.juntar(1L, 1L, 99L));
        verify(monitorUnitMapper, never()).deleteById(anyLong());
    }

    @Test
    void excluirTiraAUnidadeDeMonitoresManutencoesETelefones() {
        service.excluir(2L);

        verify(monitorUnitMapper).deleteById(2L);
        verify(monMonitorMapper).update(isNull(), any());
        verify(monManutencaoMapper).update(isNull(), any());
        verify(telefoneMapper).update(isNull(), any());
    }

    private static MonitorUnitDO unit(Long id, String nome) {
        return MonitorUnitDO.builder().id(id).name(nome).deptId(10L).build();
    }

    private static UnidadeVOs.Unidade req(Long id, String nome) {
        UnidadeVOs.Unidade u = new UnidadeVOs.Unidade();
        u.setId(id);
        u.setNome(nome);
        u.setDeptId(10L);
        u.setLatitude(new BigDecimal("-21.79"));
        u.setLongitude(new BigDecimal("-48.17"));
        return u;
    }

}
