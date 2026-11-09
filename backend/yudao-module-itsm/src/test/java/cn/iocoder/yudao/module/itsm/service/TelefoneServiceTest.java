package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.TelefoneVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.TelefoneDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.TelefoneMapper;
import cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.UnitKpiVO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import cn.iocoder.yudao.module.system.service.monitor.MonitorKpiService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelefoneServiceTest {

    private static final long UPA = 1239L;

    @Mock
    private TelefoneMapper telefoneMapper;
    @Mock
    private MonitorUnitMapper unitMapper;
    @Mock
    private MonitorKpiService kpiService;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private DeptApi deptApi;
    @Spy
    private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @InjectMocks
    private TelefoneService service;

    private static TelefoneVOs.SalvarReq req(Long id, String ramal, String telefone) {
        TelefoneVOs.SalvarReq r = new TelefoneVOs.SalvarReq();
        r.setId(id);
        r.setNome("  Almoxarifado ");
        r.setSetor("   ");
        r.setRamal(ramal);
        r.setTelefone(telefone);
        return r;
    }

    private static MonitorUnitDO upa() {
        return MonitorUnitDO.builder().id(UPA).deptId(1293L).name("UPA CENTRAL").status(0).build();
    }

    private static TelefoneDO contato(boolean publico) {
        return TelefoneDO.builder().id(1L).nome("Gerente").unidadeId(UPA).ramal("1234").celular("(16) 99111-0001")
                .whatsapp(true).celularPublico(publico).emergencia(true).build();
    }

    private static UnitKpiVO kpi(String link) {
        return UnitKpiVO.builder().unitId(UPA).link(link).build();
    }

    private void permitirCelular(boolean pode) {
        when(permissionApi.hasAnyPermissions(anyLong(), eq(TelefoneService.P_CELULAR), eq(TelefoneService.P_UPDATE))).thenReturn(pode);
    }

    private static ByteArrayInputStream planilha(String[] titulos, String[]... linhas) throws IOException {
        try (XSSFWorkbook w = new XSSFWorkbook(); ByteArrayOutputStream s = new ByteArrayOutputStream()) {
            Sheet aba = w.createSheet();
            Row c = aba.createRow(0);
            for (int i = 0; i < titulos.length; i++) {
                c.createCell(i).setCellValue(titulos[i]);
            }
            for (int l = 0; l < linhas.length; l++) {
                Row r = aba.createRow(l + 1);
                for (int i = 0; i < linhas[l].length; i++) {
                    r.createCell(i).setCellValue(linhas[l][i]);
                }
            }
            w.write(s);
            return new ByteArrayInputStream(s.toByteArray());
        }
    }

    // ------------------------------------------------------------------ cadastro

    @Test
    void semNenhumNumero_recusa() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.salvar(req(null, " ", null)));
        assertEquals(ErrorCodeConstants.TELEFONE_SEM_NUMERO.getCode(), e.getCode());
        verify(telefoneMapper, never()).insert(any(TelefoneDO.class));
    }

    @Test
    void novo_tiraEspacosEGuardaVazioComoNulo() {
        service.salvar(req(null, " 1234 ", null));
        ArgumentCaptor<TelefoneDO> c = ArgumentCaptor.forClass(TelefoneDO.class);
        verify(telefoneMapper).insert(c.capture());
        assertEquals("Almoxarifado", c.getValue().getNome());
        assertEquals("1234", c.getValue().getRamal());
        assertNull(c.getValue().getSetor());
    }

    @Test
    void soCelular_aceita() {
        TelefoneVOs.SalvarReq r = req(null, null, null);
        r.setCelular("(16) 99111-0001");
        r.setWhatsapp(true);
        service.salvar(r);
        ArgumentCaptor<TelefoneDO> c = ArgumentCaptor.forClass(TelefoneDO.class);
        verify(telefoneMapper).insert(c.capture());
        assertTrue(c.getValue().getWhatsapp());
    }

    @Test
    void whatsappSemCelular_naoFicaMarcado() {
        TelefoneVOs.SalvarReq r = req(null, "1234", null);
        r.setWhatsapp(true);
        r.setCelularPublico(true);
        service.salvar(r);
        ArgumentCaptor<TelefoneDO> c = ArgumentCaptor.forClass(TelefoneDO.class);
        verify(telefoneMapper).insert(c.capture());
        assertFalse(c.getValue().getWhatsapp());
        assertFalse(c.getValue().getCelularPublico());
    }

    @Test
    void celularSemDdd_recusa() {
        TelefoneVOs.SalvarReq r = req(null, null, null);
        r.setCelular("9 9111-00.");
        ServiceException e = assertThrows(ServiceException.class, () -> service.salvar(r));
        assertEquals(ErrorCodeConstants.TELEFONE_CELULAR_INVALIDO.getCode(), e.getCode());
    }

    @Test
    void emergenciaSemCelularOuUnidade_recusa() {
        TelefoneVOs.SalvarReq semCelular = req(null, "1234", null);
        semCelular.setUnidadeId(UPA);
        semCelular.setEmergencia(true);
        when(unitMapper.selectById(UPA)).thenReturn(upa());
        assertEquals(ErrorCodeConstants.TELEFONE_EMERGENCIA_INCOMPLETO.getCode(),
                assertThrows(ServiceException.class, () -> service.salvar(semCelular)).getCode());

        TelefoneVOs.SalvarReq semUnidade = req(null, null, null);
        semUnidade.setCelular("16991110001");
        semUnidade.setEmergencia(true);
        assertEquals(ErrorCodeConstants.TELEFONE_EMERGENCIA_INCOMPLETO.getCode(),
                assertThrows(ServiceException.class, () -> service.salvar(semUnidade)).getCode());
    }

    @Test
    void unidadeQueNaoExiste_recusa() {
        TelefoneVOs.SalvarReq r = req(null, "1234", null);
        r.setUnidadeId(9L);
        when(unitMapper.selectById(9L)).thenReturn(null);
        assertEquals(ErrorCodeConstants.TELEFONE_UNIDADE_NAO_EXISTE.getCode(),
                assertThrows(ServiceException.class, () -> service.salvar(r)).getCode());
    }

    @Test
    void editarQueNaoExiste_recusa() {
        when(telefoneMapper.selectById(9L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.salvar(req(9L, "1234", null)));
        assertEquals(ErrorCodeConstants.TELEFONE_NAO_EXISTE.getCode(), e.getCode());
        verify(telefoneMapper, never()).updateById(any(TelefoneDO.class));
    }

    @Test
    void excluirQueNaoExiste_recusa() {
        when(telefoneMapper.selectById(9L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.excluir(9L));
        verify(telefoneMapper, never()).deleteById(9L);
    }

    // ------------------------------------------------------------------ celular é dado pessoal

    @Test
    void semPermissao_naoRecebeCelularRestrito() {
        permitirCelular(false);
        when(unitMapper.selectList()).thenReturn(List.of(upa()));
        when(telefoneMapper.pagina(any(), eq(false))).thenReturn(new PageResult<>(List.of(contato(false)), 1L));
        TelefoneVOs.Resp r = service.pagina(new TelefoneVOs.PaginaReq(), 7L).getList().get(0);
        assertNull(r.getCelular());
        assertFalse(r.getWhatsapp(), "marcar WhatsApp também revelaria algo sobre o número");
        assertTrue(r.getCelularOculto());
        assertEquals("1234", r.getRamal());
    }

    @Test
    void semPermissao_recebeCelularCorporativo() {
        permitirCelular(false);
        when(unitMapper.selectList()).thenReturn(List.of(upa()));
        when(telefoneMapper.pagina(any(), eq(false))).thenReturn(new PageResult<>(List.of(contato(true)), 1L));
        TelefoneVOs.Resp r = service.pagina(new TelefoneVOs.PaginaReq(), 7L).getList().get(0);
        assertEquals("(16) 99111-0001", r.getCelular());
        assertFalse(r.getCelularOculto());
    }

    @Test
    void comPermissao_recebeCelular() {
        permitirCelular(true);
        when(unitMapper.selectList()).thenReturn(List.of(upa()));
        when(telefoneMapper.pagina(any(), eq(true))).thenReturn(new PageResult<>(List.of(contato(false)), 1L));
        TelefoneVOs.Resp r = service.pagina(new TelefoneVOs.PaginaReq(), 7L).getList().get(0);
        assertEquals("(16) 99111-0001", r.getCelular());
        assertTrue(r.getWhatsapp());
        assertEquals("UPA CENTRAL", r.getUnidade());
    }

    @Test
    void exportar_semPermissao_naoTrazCelularNemWhatsapp() {
        permitirCelular(false);
        when(unitMapper.selectList()).thenReturn(List.of(upa()));
        when(telefoneMapper.pagina(any(), eq(false))).thenReturn(new PageResult<>(List.of(contato(false)), 1L));
        cn.iocoder.yudao.framework.excel.core.util.Planilha.Aba aba = service.planilha(new TelefoneVOs.PaginaReq(), 7L).get(0);
        assertFalse(aba.colunas().contains("Celular"), "reimportar o arquivo apagaria os números");
        assertFalse(aba.colunas().contains("WhatsApp"));
        assertEquals(aba.colunas().size(), aba.linhas().get(0).size());
        assertEquals("1234", aba.linhas().get(0).get(aba.colunas().indexOf("Ramal")));
    }

    @Test
    void exportar_comPermissao_trazColunasDoModelo() {
        permitirCelular(true);
        when(unitMapper.selectList()).thenReturn(List.of(upa()));
        when(telefoneMapper.pagina(any(), eq(true))).thenReturn(new PageResult<>(List.of(contato(false)), 1L));
        cn.iocoder.yudao.framework.excel.core.util.Planilha.Aba aba = service.planilha(new TelefoneVOs.PaginaReq(), 7L).get(0);
        assertEquals(TelefonePlanilha.COLUNAS, aba.colunas());
        List<Object> linha = aba.linhas().get(0);
        assertEquals("(16) 99111-0001", linha.get(aba.colunas().indexOf("Celular")));
        assertEquals("Sim", linha.get(aba.colunas().indexOf("WhatsApp")));
        assertEquals("UPA CENTRAL", linha.get(aba.colunas().indexOf("Unidade")));
    }

    // ------------------------------------------------------------------ vínculo com a unidade

    @Test
    void linkDaUnidadeFora_avisa() {
        permitirCelular(true);
        when(unitMapper.selectById(UPA)).thenReturn(upa());
        when(kpiService.getKpi(UPA)).thenReturn(kpi("offline"));
        when(telefoneMapper.daUnidade(UPA)).thenReturn(List.of(contato(false)));
        TelefoneVOs.ContatosUnidade r = service.daUnidade(UPA, null, 7L);
        assertTrue(r.getLinkFora());
        assertTrue(r.getContatos().get(0).getLinkFora());
    }

    @Test
    void linkLentoOuSemLeitura_naoAvisa() {
        when(kpiService.getKpi(UPA)).thenReturn(kpi("degradado"));
        when(unitMapper.selectActiveList()).thenReturn(List.of(upa()));
        assertFalse(service.unidades().get(0).getLinkFora());
        when(kpiService.getKpi(UPA)).thenReturn(null);
        assertFalse(service.unidades().get(0).getLinkFora());
    }

    @Test
    void setorFilho_achaAUnidade() {
        permitirCelular(true);
        when(unitMapper.selectActiveList()).thenReturn(List.of(upa()));
        DeptRespDTO enfermaria = new DeptRespDTO();
        enfermaria.setId(5000L);
        enfermaria.setParentId(1293L);
        when(deptApi.getDept(5000L)).thenReturn(enfermaria);
        when(telefoneMapper.daUnidade(UPA)).thenReturn(List.of());
        assertEquals(UPA, service.daUnidade(null, 5000L, 7L).getUnidadeId());
    }

    @Test
    void setorForaDeQualquerUnidade_naoTemContatos() {
        when(unitMapper.selectActiveList()).thenReturn(List.of(upa()));
        when(deptApi.getDept(1L)).thenReturn(null);
        assertNull(service.daUnidade(null, 1L, 7L));
        verifyNoInteractions(telefoneMapper);
    }

    // ------------------------------------------------------------------ planilha

    private static final String[] TITULOS = {"E-MAIL", "Nome", "Unidade", "Celular", "WhatsApp", "Emergência"};

    @Test
    void importa_colunasEmQualquerOrdem_eLinhaRuimNaoParaAsOutras() throws Exception {
        when(unitMapper.selectActiveList()).thenReturn(List.of(upa()));
        when(unitMapper.selectById(UPA)).thenReturn(upa());
        when(telefoneMapper.selectList()).thenReturn(List.of());
        TelefoneVOs.ImportacaoResp r = service.importar(planilha(TITULOS,
                new String[]{"g@x.gov.br", "Gerente", "upa central", "16991110001", "sim", "x"},
                new String[]{"", "Alguém", "UPA QUE NAO EXISTE", "16991110002", "", ""},
                new String[]{"", "Outro", "UPA CENTRAL", "16991110003", "talvez", ""},
                new String[]{"", "", "", "", "", ""},
                new String[]{"", "Sem número", "UPA CENTRAL", "", "", ""}));
        assertEquals(1, r.getCriados());
        assertEquals(3, r.getRecusados());
        assertTrue(r.getErros().get(0).startsWith("Linha 3:"), r.getErros().get(0));
        assertTrue(r.getErros().get(1).contains("talvez"));
        assertTrue(r.getErros().get(2).startsWith("Linha 6:"), "a linha em branco é pulada, sem mudar a numeração");
        ArgumentCaptor<TelefoneDO> c = ArgumentCaptor.forClass(TelefoneDO.class);
        verify(telefoneMapper).insert(c.capture());
        assertEquals(UPA, c.getValue().getUnidadeId());
        assertTrue(c.getValue().getWhatsapp());
        assertTrue(c.getValue().getEmergencia());
        assertEquals("g@x.gov.br", c.getValue().getEmail());
    }

    @Test
    void importa_mesmoNomeNaMesmaUnidade_atualiza_eColunaAusenteMantemValor() throws Exception {
        when(unitMapper.selectActiveList()).thenReturn(List.of(upa()));
        when(unitMapper.selectById(UPA)).thenReturn(upa());
        when(telefoneMapper.selectList()).thenReturn(new ArrayList<>(List.of(contato(true))));
        TelefoneVOs.ImportacaoResp r = service.importar(planilha(new String[]{"Nome", "Unidade", "Ramal"},
                new String[]{"GERENTE", "UPA CENTRAL", "4321"}));
        assertEquals(1, r.getAtualizados());
        ArgumentCaptor<TelefoneDO> c = ArgumentCaptor.forClass(TelefoneDO.class);
        verify(telefoneMapper).updateById(c.capture());
        assertEquals("4321", c.getValue().getRamal());
        assertEquals("(16) 99111-0001", c.getValue().getCelular());
        assertTrue(c.getValue().getWhatsapp());
        assertTrue(c.getValue().getCelularPublico());
    }

    @Test
    void importa_duasVezesNoMesmoArquivo_naoDuplica() throws Exception {
        when(unitMapper.selectActiveList()).thenReturn(List.of());
        when(telefoneMapper.selectList()).thenReturn(List.of());
        doAnswer(i -> {
            i.<TelefoneDO>getArgument(0).setId(5L);
            return 1;
        }).when(telefoneMapper).insert(any(TelefoneDO.class));
        TelefoneVOs.ImportacaoResp r = service.importar(planilha(new String[]{"Nome", "Ramal"},
                new String[]{"Portaria", "100"}, new String[]{"portaria", "200"}));
        assertEquals(1, r.getCriados());
        assertEquals(1, r.getAtualizados());
    }

    @Test
    void importa_celularDigitadoComoNumero_naoViraNotacaoCientifica() throws Exception {
        when(unitMapper.selectActiveList()).thenReturn(List.of());
        when(telefoneMapper.selectList()).thenReturn(List.of());
        try (XSSFWorkbook w = new XSSFWorkbook(); ByteArrayOutputStream s = new ByteArrayOutputStream()) {
            Sheet aba = w.createSheet();
            aba.createRow(0).createCell(0).setCellValue("Nome");
            aba.getRow(0).createCell(1).setCellValue("Celular");
            aba.createRow(1).createCell(0).setCellValue("Plantão");
            aba.getRow(1).createCell(1).setCellValue(5516991110001d);
            w.write(s);
            service.importar(new ByteArrayInputStream(s.toByteArray()));
        }
        ArgumentCaptor<TelefoneDO> c = ArgumentCaptor.forClass(TelefoneDO.class);
        verify(telefoneMapper).insert(c.capture());
        assertEquals("5516991110001", c.getValue().getCelular());
    }

    @Test
    void importa_arquivoQueNaoEPlanilha_recusa() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.importar(new ByteArrayInputStream("nome;ramal".getBytes())));
        assertEquals(ErrorCodeConstants.TELEFONE_PLANILHA_INVALIDA.getCode(), e.getCode());
    }

    @Test
    void importa_semColunaNome_recusa() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.importar(planilha(new String[]{"Ramal"}, new String[]{"100"})));
        assertEquals(ErrorCodeConstants.TELEFONE_PLANILHA_INVALIDA.getCode(), e.getCode());
    }

    @Test
    void modelo_trazAsUnidadesEmOutraAba() {
        when(unitMapper.selectActiveList()).thenReturn(List.of(upa()));
        var abas = service.modelo();
        assertEquals("Contatos", abas.get(0).nome());
        assertTrue(abas.get(0).linhas().isEmpty(), "linha de exemplo seria importada por engano");
        assertEquals(List.of(List.of("UPA CENTRAL")), abas.get(1).linhas());
    }

}
