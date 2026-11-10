package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.UnidadeVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** O arquivo do Exportar tem de voltar pelo Importar, e nada pode ser removido sem o usuário escolher. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportacaoPlanilhaTest {

    private static final List<String> COLUNAS_MONITOR = List.of("Nome", "Tipo", "Endereço", "Porta", "Unidade", "Papel", "Situação",
            "Intervalo (s)", "Última verificação", "Latência (ms)", "Disponibilidade 24h (%)", "Disponibilidade 30d (%)",
            "Certificado vence em (dias)", "Descrição");
    private static final List<String> COLUNAS_UNIDADE = List.of("Unidade", "Departamento", "CEP", "Logradouro", "Número", "Complemento",
            "Bairro", "Cidade", "UF", "Latitude", "Longitude", "Ativa", "Monitores");

    @Mock
    private MonitorUnitMapper unitMapper;
    @Mock
    private DeptApi deptApi;
    @Mock
    private DeptService deptService;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private cn.iocoder.yudao.module.system.service.permission.PermissionService permissao;
    @InjectMocks
    @Spy
    private MonitorService monitorService;
    @InjectMocks
    @Spy
    private UnidadeService unidadeService;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ImportacaoMonitorService monitores = new ImportacaoMonitorService();
    private final ImportacaoUnidadeService unidades = new ImportacaoUnidadeService();

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(monitorService, "monitorUnitMapper", unitMapper);
        ReflectionTestUtils.setField(unidadeService, "deptApi", deptApi);
        ReflectionTestUtils.setField(monitores, "monitorService", monitorService);
        ReflectionTestUtils.setField(monitores, "monitorUnitMapper", unitMapper);
        ReflectionTestUtils.setField(monitores, "auditoriaService", auditoria);
        ReflectionTestUtils.setField(monitores, "validator", validator);
        ReflectionTestUtils.setField(unidades, "unidadeService", unidadeService);
        ReflectionTestUtils.setField(unidades, "deptService", deptService);
        ReflectionTestUtils.setField(unidades, "auditoriaService", auditoria);
        ReflectionTestUtils.setField(unidades, "validator", validator);
        ReflectionTestUtils.setField(unidades, "permissionService", permissao);

        MonitorUnitDO upa = MonitorUnitDO.builder().id(1L).name("UPA CENTRAL").deptId(10L).status(0).build();
        when(unitMapper.selectList()).thenReturn(List.of(upa));
        when(unitMapper.selectById(any())).thenReturn(upa);
        when(deptApi.getDept(any())).thenReturn(new DeptRespDTO());
        DeptDO saude = new DeptDO();
        saude.setId(10L);
        saude.setName("Saúde");
        DeptDO educacao = new DeptDO();
        educacao.setId(20L);
        educacao.setName("Educação");
        when(deptService.getDeptList(any(cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO.class)))
                .thenReturn(List.of(saude, educacao));
        doNothing().when(monitorService).aplicarImportacao(any(), anyCollection());
        doNothing().when(unidadeService).aplicarImportacao(any(), anyCollection());
    }

    private static ByteArrayInputStream arquivo(List<String> colunas, List<List<Object>> linhas) {
        return new ByteArrayInputStream(Planilha.gerar(List.of(new Planilha.Aba("Dados", colunas, linhas))));
    }

    /** Linha como o Exportar de monitores escreve */
    private static List<Object> linhaMonitor(String nome, String tipo, String alvo, Integer porta, String unidade, String situacao, Integer intervalo, String descricao) {
        return Arrays.asList(nome, tipo, alvo, porta, unidade, "servico", situacao, intervalo, "09/11/2026 08:05", 12, 99.5d, 99.9d, 30, descricao);
    }

    private static MonitorDO monitor(long id, String nome, String alvo) {
        return MonitorDO.builder().id(id).nome(nome).tipo("ping").alvo(alvo).metodo("GET").unidadeId(1L).papel("servico").ativo(true)
                .intervaloS(60).descricao("Link principal").build();
    }

    private static UnidadeVOs.Unidade unidade(long id, String nome, int monitores) {
        UnidadeVOs.Unidade u = new UnidadeVOs.Unidade();
        u.setId(id);
        u.setNome(nome);
        u.setDeptId(10L);
        u.setDeptNome("Saúde");
        u.setCep("14800-000");
        u.setLogradouro("Rua Um");
        u.setCidade("Araraquara");
        u.setUf("SP");
        u.setLatitude(new BigDecimal("-21.7945000"));
        u.setLongitude(new BigDecimal("-48.1756000"));
        u.setAtiva(true);
        u.setMonitores(monitores);
        return u;
    }

    private static List<Object> linhaUnidade(String nome, String dept, String complemento, Object lat, Object lon, String ativa) {
        return Arrays.asList(nome, dept, "14800-000", "Rua Um", null, complemento, null, "Araraquara", "SP", lat, lon, ativa, 3);
    }

    private static ImportacaoVOs.Mudanca mudanca(ImportacaoVOs.Item item, String campo) {
        return item.getMudancas().stream().filter(m -> m.getCampo().equals(campo)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ monitores

    @Test
    void arquivoExportadoSemAlteracaoNaoMudaNada() {
        doReturn(List.of(monitor(1, "Link UPA", "10.0.0.1"))).when(monitorService).todos();

        ImportacaoVOs.Previa p = monitores.analisar(arquivo(COLUNAS_MONITOR,
                List.of(linhaMonitor("Link UPA", "ping", "10.0.0.1", null, "UPA CENTRAL", "fora do ar", 60, "Link principal"))));

        assertEquals(1, p.getSemMudanca(), "estado e métricas do arquivo não são configuração");
        assertTrue(p.getNovos().isEmpty());
        assertTrue(p.getAlterados().isEmpty());
        assertTrue(p.getAusentes().isEmpty());
        assertEquals(List.of(), p.getErros());
    }

    @Test
    void separaAdicionadosAtualizadosERemovidos() {
        doReturn(List.of(monitor(1, "Link UPA", "10.0.0.1"), monitor(2, "Antigo", "10.0.0.2"))).when(monitorService).todos();

        ImportacaoVOs.Previa p = monitores.analisar(arquivo(COLUNAS_MONITOR, List.of(
                linhaMonitor("link upa", "ping", "10.0.0.9", null, null, "pausado", 120, null),
                linhaMonitor("Novo", "tcp", "10.0.0.3", 443, "UPA CENTRAL", "no ar", 30, "Banco"))));

        assertEquals(1, p.getNovos().size());
        assertEquals("443", mudanca(p.getNovos().get(0), "Porta").getPara());
        assertEquals(1, p.getAlterados().size());
        ImportacaoVOs.Item alterado = p.getAlterados().get(0);
        assertEquals("10.0.0.1", mudanca(alterado, "Endereço").getDe());
        assertEquals("10.0.0.9", mudanca(alterado, "Endereço").getPara());
        assertEquals("pausado", mudanca(alterado, "Situação").getPara());
        assertNull(mudanca(alterado, "Unidade").getPara(), "célula em branco remove o dado");
        assertNull(mudanca(alterado, "Descrição").getPara(), "célula em branco remove o dado");
        assertEquals(List.of(2L), p.getAusentes().stream().map(ImportacaoVOs.Item::getId).toList());
    }

    @Test
    void ausenteSoSaiSeForEscolhido() {
        doReturn(List.of(monitor(1, "Link UPA", "10.0.0.1"), monitor(2, "Antigo", "10.0.0.2"))).when(monitorService).todos();
        List<List<Object>> linhas = List.of(linhaMonitor("Link UPA", "ping", "10.0.0.1", null, "UPA CENTRAL", "no ar", 60, "Link principal"));

        assertEquals(0, monitores.aplicar(arquivo(COLUNAS_MONITOR, linhas), null, 7L).getRemovidos());
        verify(monitorService).aplicarImportacao(List.of(), Set.of());

        ImportacaoVOs.Resultado r = monitores.aplicar(arquivo(COLUNAS_MONITOR, linhas), List.of(2L, 999L, 1L), 7L);
        assertEquals(1, r.getRemovidos(), "só o ausente; id de quem está no arquivo ou inexistente é ignorado");
        verify(monitorService).aplicarImportacao(List.of(), Set.of(2L));
    }

    @Test
    void linhaRecusadaNaoViraRemocao() {
        doReturn(List.of(monitor(1, "Link UPA", "10.0.0.1"))).when(monitorService).todos();

        ImportacaoVOs.Previa p = monitores.analisar(arquivo(COLUNAS_MONITOR,
                List.of(linhaMonitor("Link UPA", "inventado", "10.0.0.1", null, null, "no ar", 60, null))));

        assertEquals(1, p.getRecusados());
        assertTrue(p.getErros().get(0).startsWith("Linha 2: "), p.getErros().get(0));
        assertTrue(p.getAusentes().isEmpty(), "o monitor está no arquivo, só que com erro");
    }

    @Test
    void naoGravaCanaisNemTrocaOQueNaoEstaNaPlanilha() {
        MonitorDO atual = monitor(1, "Site", "https://a.gov.br");
        atual.setTipo("http");
        atual.setCabecalhos("{\"Authorization\":\"Bearer x\"}");
        doReturn(List.of(atual)).when(monitorService).todos();

        monitores.aplicar(arquivo(COLUNAS_MONITOR,
                List.of(linhaMonitor("Site", "http", "https://b.gov.br", null, "UPA CENTRAL", "no ar", 60, "Link principal"))), null, 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MonitorVOs.Monitor>> gravados = ArgumentCaptor.forClass(List.class);
        verify(monitorService).aplicarImportacao(gravados.capture(), any());
        MonitorVOs.Monitor vo = gravados.getValue().get(0);
        assertEquals(1L, vo.getId());
        assertEquals("https://b.gov.br", vo.getAlvo());
        assertEquals("{\"Authorization\":\"Bearer x\"}", vo.getCabecalhos());
        assertNull(vo.getCanalIds(), "nulo mantém os canais de aviso");
    }

    @Test
    void nomeRepetidoDesempataPeloEnderecoESemDesempateRecusa() {
        doReturn(List.of(monitor(1, "Link", "10.0.0.1"), monitor(2, "Link", "10.0.0.2"))).when(monitorService).todos();

        ImportacaoVOs.Previa p = monitores.analisar(arquivo(COLUNAS_MONITOR, List.of(
                linhaMonitor("Link", "ping", "10.0.0.2", null, "UPA CENTRAL", "no ar", 90, "Link principal"),
                linhaMonitor("Link", "ping", "10.0.0.8", null, "UPA CENTRAL", "no ar", 90, "Link principal"))));

        assertEquals(List.of(2L), p.getAlterados().stream().map(ImportacaoVOs.Item::getId).toList());
        assertEquals(1, p.getRecusados());
        assertTrue(p.getAusentes().isEmpty());
    }

    @Test
    void colunaQueNaoVeioMantemOCadastro() {
        doReturn(List.of(monitor(1, "Link UPA", "10.0.0.1"))).when(monitorService).todos();

        ImportacaoVOs.Previa p = monitores.analisar(arquivo(List.of("Nome", "Intervalo (s)"), List.of(Arrays.asList("Link UPA", 300))));

        assertEquals(1, p.getAlterados().size());
        assertEquals(1, p.getAlterados().get(0).getMudancas().size());
        assertEquals("300", mudanca(p.getAlterados().get(0), "Intervalo (s)").getPara());
    }

    @Test
    void arquivoVazioOuSemColunaEhRecusadoInteiro() {
        assertThrows(ServiceException.class, () -> monitores.analisar(arquivo(COLUNAS_MONITOR, List.of())));
        assertThrows(ServiceException.class, () -> monitores.analisar(arquivo(List.of("Endereço"), List.of(Arrays.<Object>asList("10.0.0.1")))));
        assertThrows(ServiceException.class, () -> monitores.analisar(new ByteArrayInputStream("nome;tipo".getBytes())));
    }

    @Test
    void repetidoNoArquivoEhRecusado() {
        doReturn(new ArrayList<>()).when(monitorService).todos();

        ImportacaoVOs.Previa p = monitores.analisar(arquivo(COLUNAS_MONITOR, List.of(
                linhaMonitor("Novo", "ping", "10.0.0.3", null, null, "no ar", 60, null),
                linhaMonitor("NOVO", "ping", "10.0.0.4", null, null, "no ar", 60, null))));

        assertEquals(1, p.getNovos().size());
        assertEquals(1, p.getRecusados());
    }

    // ------------------------------------------------------------------ unidades

    @Test
    void unidadeExportadaSemAlteracaoNaoMudaNada() {
        doReturn(List.of(unidade(1, "UPA CENTRAL", 3))).when(unidadeService).listar();

        ImportacaoVOs.Previa p = unidades.analisar(arquivo(COLUNAS_UNIDADE,
                List.of(linhaUnidade("UPA CENTRAL", "Saúde", null, new BigDecimal("-21.7945"), new BigDecimal("-48.1756"), "Sim"))));

        assertEquals(1, p.getSemMudanca());
        assertEquals(List.of(), p.getErros());
    }

    @Test
    void unidadeMostraMudancasEAvisaMonitoresSemUnidade() {
        doReturn(List.of(unidade(1, "UPA CENTRAL", 0), unidade(2, "CEMEI", 4))).when(unidadeService).listar();

        ImportacaoVOs.Previa p = unidades.analisar(arquivo(COLUNAS_UNIDADE, List.of(
                linhaUnidade("UPA CENTRAL", "Educação", "Fundos", "-21,8", "-48,2", "Não"),
                linhaUnidade("Nova", "Saúde", null, -21.5d, -48.5d, "Sim"))));

        ImportacaoVOs.Item alterada = p.getAlterados().get(0);
        assertEquals("Educação", mudanca(alterada, "Departamento").getPara());
        assertEquals("Fundos", mudanca(alterada, "Complemento").getPara());
        assertNull(mudanca(alterada, "Complemento").getDe());
        assertEquals("-21.8", mudanca(alterada, "Latitude").getPara());
        assertEquals("Não", mudanca(alterada, "Ativa").getPara());
        assertEquals(1, p.getNovos().size());
        assertEquals("4 monitor(es) ficarão sem unidade.", p.getAusentes().get(0).getAviso());
    }

    @Test
    void unidadeNovaPrecisaDeDepartamentoECoordenadas() {
        doReturn(List.of()).when(unidadeService).listar();

        ImportacaoVOs.Previa p = unidades.analisar(arquivo(COLUNAS_UNIDADE, List.of(
                linhaUnidade("Sem dept", null, null, -21.5d, -48.5d, "Sim"),
                linhaUnidade("Sem mapa", "Saúde", null, null, null, "Sim"),
                linhaUnidade("Dept errado", "Obras", null, -21.5d, -48.5d, "Sim"))));

        assertEquals(3, p.getRecusados());
        assertTrue(p.getNovos().isEmpty());
    }

    @Test
    void unidadeSoSaiSeForEscolhida() {
        doReturn(List.of(unidade(1, "UPA CENTRAL", 0), unidade(2, "CEMEI", 4))).when(unidadeService).listar();
        List<List<Object>> linhas = List.of(linhaUnidade("UPA CENTRAL", "Saúde", null, new BigDecimal("-21.7945"), new BigDecimal("-48.1756"), "Sim"));

        unidades.aplicar(arquivo(COLUNAS_UNIDADE, linhas), List.of(), 7L);
        verify(unidadeService).aplicarImportacao(List.of(), Set.of());

        assertEquals(1, unidades.aplicar(arquivo(COLUNAS_UNIDADE, linhas), List.of(2L), 7L).getRemovidos());
        verify(unidadeService).aplicarImportacao(List.of(), Set.of(2L));
    }

    @Test
    void semPermissaoDepartamentoQueFaltaRecusaALinha() {
        doReturn(List.of()).when(unidadeService).listar();

        ImportacaoVOs.Resultado r = unidades.aplicar(arquivo(COLUNAS_UNIDADE, List.of(
                linhaUnidade("CAPS", "CAPS", null, -21.5d, -48.5d, "Sim"))), List.of(), 7L);

        assertEquals(1, r.getRecusados());
        verify(deptService, never()).createDept(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void comPermissaoCriaDepartamentoSobOSuperiorOuSobARaiz() {
        doReturn(List.of()).when(unidadeService).listar();
        when(permissao.hasAnyPermissions(eq(7L), any(String[].class))).thenReturn(true);
        DeptDO raiz = new DeptDO();
        raiz.setId(100L);
        raiz.setName("Empresa");
        raiz.setParentId(0L);
        DeptDO saude = new DeptDO();
        saude.setId(10L);
        saude.setName("Saúde");
        saude.setParentId(100L);
        when(deptService.getDeptList(any(cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO.class)))
                .thenReturn(List.of(raiz, saude));
        java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong(500);
        when(deptService.createDept(any())).thenAnswer(i -> ids.incrementAndGet());
        List<String> colunas = List.of("Unidade", "Departamento", "Departamento superior", "Latitude", "Longitude");
        ByteArrayInputStream arq = arquivo(colunas, List.of(Arrays.asList("CAPS", "CAPS", "Saúde", -21.5d, -48.5d),
                Arrays.asList("UBS Nova", "UBS Nova", "Atenção Básica", -21.5d, -48.5d),
                Arrays.asList("Avulsa", "Avulsa", null, -21.5d, -48.5d)));

        ImportacaoVOs.Previa p = unidades.analisar(arquivo(colunas, List.of(Arrays.asList("CAPS", "CAPS", "Saúde", -21.5d, -48.5d))));
        // sem usuário logado vale como sem permissão
        assertEquals(1, p.getRecusados(), String.valueOf(p.getErros()));
        unidades.aplicar(arq, List.of(), 7L);

        org.mockito.ArgumentCaptor<cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO> criados =
                org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO.class);
        verify(deptService, times(4)).createDept(criados.capture());
        java.util.Map<String, Long> pai = new java.util.HashMap<>();
        criados.getAllValues().forEach(d -> pai.put(d.getName(), d.getParentId()));
        assertEquals(10L, pai.get("CAPS"));
        assertEquals(100L, pai.get("Atenção Básica"));
        assertEquals(100L, pai.get("Avulsa"));
        // o superior novo é criado antes do filho, que recebe o id dele
        assertTrue(pai.get("UBS Nova") > 500L);
        org.mockito.ArgumentCaptor<List<UnidadeVOs.Unidade>> gravadas = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(unidadeService).aplicarImportacao(gravadas.capture(), anyCollection());
        assertTrue(gravadas.getValue().stream().allMatch(u -> u.getDeptId() != null && u.getDeptId() > 500L));
    }

}
