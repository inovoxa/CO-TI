package cn.iocoder.yudao.module.cofre.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.cofre.controller.admin.vo.CofreVOs;
import cn.iocoder.yudao.module.cofre.dal.dataobject.PastaDO;
import cn.iocoder.yudao.module.cofre.dal.dataobject.PastaMembroDO;
import cn.iocoder.yudao.module.cofre.dal.dataobject.SegredoDO;
import cn.iocoder.yudao.module.cofre.dal.mysql.PastaMapper;
import cn.iocoder.yudao.module.cofre.dal.mysql.PastaMembroMapper;
import cn.iocoder.yudao.module.cofre.dal.mysql.SegredoMapper;
import cn.iocoder.yudao.module.cofre.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.identidade.service.MfaService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
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

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CofreServiceTest {

    private static final long PASTA = 1L;
    private static final long SEGREDO = 10L;
    private static final long PESSOA = 7L;

    @Mock
    private PastaMapper pastaMapper;
    @Mock
    private PastaMembroMapper membroMapper;
    @Mock
    private SegredoMapper segredoMapper;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private MfaService mfaService;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;
    @Spy
    private CifradorSegredos cifrador = new CifradorSegredos(Base64.getEncoder().encodeToString(new byte[32]));
    @InjectMocks
    private CofreService service;

    @BeforeEach
    void preparar() {
        when(pastaMapper.selectById(PASTA)).thenReturn(PastaDO.builder().id(PASTA).nome("Servidores").build());
        when(mfaService.exige(PESSOA)).thenReturn(true);
        String cifrada = cifrador.cifrar("s3nha", CofreService.contextoSenha(SEGREDO));
        when(segredoMapper.selectById(SEGREDO)).thenReturn(SegredoDO.builder().id(SEGREDO).pastaId(PASTA).nome("root")
                .senhaCifrada(cifrada).build());
    }

    private void papel(Integer papel) {
        when(membroMapper.selectOne(any())).thenReturn(papel == null ? null
                : PastaMembroDO.builder().id(99L).pastaId(PASTA).userId(PESSOA).papel(papel).build());
    }

    private static void falhaCom(int codigo, org.junit.jupiter.api.function.Executable acao) {
        assertEquals(codigo, assertThrows(ServiceException.class, acao).getCode());
    }

    @Test
    void membroVeASenhaEFicaNaTrilhaSemOValor() {
        papel(PastaMembroDO.VER);

        assertEquals("s3nha", service.revelar(SEGREDO, PESSOA).getSenha());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detalhes = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq("cofre.senha_vista"), eq(PESSOA), isNull(), detalhes.capture());
        assertFalse(detalhes.getValue().toString().contains("s3nha"));
    }

    @Test
    void quemNaoEMembroNaoVeMesmoAdministrandoOCofre() {
        papel(null);
        when(permissionApi.hasAnyPermissions(PESSOA, CofreService.PERMISSAO_ADMINISTRAR)).thenReturn(true);

        falhaCom(ErrorCodeConstants.PASTA_SEM_ACESSO.getCode(), () -> service.revelar(SEGREDO, PESSOA));
        verifyNoInteractions(auditoria);
    }

    @Test
    void semVerificacaoEmDuasEtapasNaoVe() {
        papel(PastaMembroDO.GERENCIAR);
        when(mfaService.exige(PESSOA)).thenReturn(false);

        falhaCom(ErrorCodeConstants.COFRE_EXIGE_MFA.getCode(), () -> service.revelar(SEGREDO, PESSOA));
    }

    @Test
    void senhaFalhandoNaAuditoriaNaoSai() {
        papel(PastaMembroDO.VER);
        doThrow(new IllegalStateException("banco fora")).when(auditoria).registrar(any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> service.revelar(SEGREDO, PESSOA));
    }

    @Test
    void textoCifradoCopiadoDeOutraLinhaNaoAbre() {
        papel(PastaMembroDO.VER);
        String deOutraLinha = cifrador.cifrar("outra", CofreService.contextoSenha(11L));
        when(segredoMapper.selectById(SEGREDO)).thenReturn(SegredoDO.builder().id(SEGREDO).pastaId(PASTA).nome("root")
                .senhaCifrada(deOutraLinha).build());

        assertThrows(IllegalStateException.class, () -> service.revelar(SEGREDO, PESSOA));
    }

    @Test
    void quemSoVeNaoCadastraNemExclui() {
        papel(PastaMembroDO.VER);
        CofreVOs.SegredoReq req = new CofreVOs.SegredoReq();
        req.setPastaId(PASTA);
        req.setNome("novo");
        req.setSenha("x");

        falhaCom(ErrorCodeConstants.PASTA_SEM_ACESSO.getCode(), () -> service.salvarSegredo(req, PESSOA));
        falhaCom(ErrorCodeConstants.PASTA_SEM_ACESSO.getCode(), () -> service.excluirSegredo(SEGREDO, PESSOA));
        verify(segredoMapper, never()).insert(any(SegredoDO.class));
    }

    @Test
    void senhaEntraCifradaComOIdDoSegredo() {
        papel(PastaMembroDO.EDITAR);
        when(segredoMapper.proximoId()).thenReturn(50L);
        CofreVOs.SegredoReq req = new CofreVOs.SegredoReq();
        req.setPastaId(PASTA);
        req.setNome("novo");
        req.setSenha("s3nha");
        req.setNotas("nota");

        service.salvarSegredo(req, PESSOA);

        ArgumentCaptor<SegredoDO> s = ArgumentCaptor.forClass(SegredoDO.class);
        verify(segredoMapper).insert(s.capture());
        assertFalse(s.getValue().getSenhaCifrada().contains("s3nha"));
        assertEquals("s3nha", cifrador.decifrar(s.getValue().getSenhaCifrada(), CofreService.contextoSenha(50L)));
        assertEquals("nota", cifrador.decifrar(s.getValue().getNotasCifradas(), CofreService.contextoNotas(50L)));
    }

    @Test
    void cadastrarSemSenhaFalha() {
        papel(PastaMembroDO.EDITAR);
        CofreVOs.SegredoReq req = new CofreVOs.SegredoReq();
        req.setPastaId(PASTA);
        req.setNome("novo");

        falhaCom(ErrorCodeConstants.SEGREDO_SENHA_OBRIGATORIA.getCode(), () -> service.salvarSegredo(req, PESSOA));
    }

    @Test
    void ultimoGestorNaoSaiDaPasta() {
        papel(PastaMembroDO.GERENCIAR);
        when(membroMapper.selectList(any(SFunction.class), eq(PASTA))).thenReturn(List.of(
                PastaMembroDO.builder().pastaId(PASTA).userId(PESSOA).papel(PastaMembroDO.GERENCIAR).build()));

        falhaCom(ErrorCodeConstants.PASTA_ULTIMO_GESTOR.getCode(), () -> service.removerMembro(PASTA, PESSOA, PESSOA));
        verify(membroMapper, never()).deleteById(anyLong());
    }

    @Test
    void listaDeSenhasDeOutraPastaEhRecusada() {
        when(membroMapper.selectList(any(SFunction.class), eq(PESSOA))).thenReturn(List.of());

        falhaCom(ErrorCodeConstants.PASTA_SEM_ACESSO.getCode(), () -> service.segredos(PASTA, null, PESSOA));
    }

}
