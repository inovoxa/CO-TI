package cn.iocoder.yudao.module.assistente.service;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.TelefoneVOs;
import cn.iocoder.yudao.module.itsm.service.TelefoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FerramentasInternasContatosTest {

    private static final String P = "itsm:telefone:query";
    private static final long USUARIO = 7L;

    @Mock
    private PermissionCommonApi permissionApi;
    @Mock
    private TelefoneService telefoneService;
    @InjectMocks
    private FerramentasInternas ferramentas;

    @BeforeEach
    void pode() {
        when(permissionApi.hasAnyPermissions(anyLong(), eq(P))).thenReturn(true);
    }

    private static TelefoneVOs.Unidade unidade(long id, String nome, boolean fora) {
        TelefoneVOs.Unidade u = new TelefoneVOs.Unidade();
        u.setId(id);
        u.setNome(nome);
        u.setLinkFora(fora);
        return u;
    }

    private static TelefoneVOs.Resp contato(String nome, boolean emergencia) {
        TelefoneVOs.Resp t = new TelefoneVOs.Resp();
        t.setNome(nome);
        t.setEmergencia(emergencia);
        t.setUnidade("UPA CENTRAL");
        t.setRamal("1234");
        return t;
    }

    private void lista(TelefoneVOs.Resp... ts) {
        when(telefoneService.pagina(any(), anyLong())).thenReturn(new PageResult<>(List.of(ts), (long) ts.length));
    }

    @Test
    void semPermissao_nemApareceNemExecuta() {
        when(permissionApi.hasAnyPermissions(anyLong(), eq(P))).thenReturn(false);
        assertFalse(ferramentas.definicoes(USUARIO).toString().contains("buscar_contatos"));
        assertTrue(ferramentas.executar("buscar_contatos", Map.of("busca", "gerente"), USUARIO).contains("não tem permissão"));
        verify(telefoneService, never()).pagina(any(), anyLong());
    }

    @Test
    void comPermissao_aparece() {
        assertTrue(ferramentas.definicoes(USUARIO).toString().contains("buscar_contatos"));
    }

    @Test
    void semFiltro_pedeBusca() {
        assertTrue(ferramentas.executar("buscar_contatos", Map.of(), USUARIO).contains("informe a busca"));
    }

    @Test
    void celularRestrito_naoChegaAoModelo_eEleSabeQueExiste() {
        TelefoneVOs.Resp t = contato("Gerente", true);
        t.setCelularOculto(true);
        t.setWhatsapp(false);
        lista(t);
        String json = ferramentas.executar("buscar_contatos", Map.of("busca", "gerente"), USUARIO);
        assertTrue(json.contains("celular_restrito"), json);
        assertFalse(json.contains("\"celular\""), json);
        assertFalse(json.contains("wa.me"), json);
        verify(telefoneService).pagina(any(), eq(USUARIO));
    }

    @Test
    void celularVisivel_trazLinkDoWhatsappComCodigoDoPais() {
        TelefoneVOs.Resp t = contato("Gerente", true);
        t.setCelular("(16) 99111-0001");
        t.setWhatsapp(true);
        lista(t);
        String json = ferramentas.executar("buscar_contatos", Map.of("busca", "gerente"), USUARIO);
        assertTrue(json.contains("https://wa.me/5516991110001"), json);
    }

    @Test
    void unidadeSemAcento_acha_eAvisaLinkFora_mesmoSemContato() {
        when(telefoneService.unidades()).thenReturn(List.of(unidade(1, "USF SÃO BENTO", true), unidade(2, "UPA CENTRAL", false)));
        lista();
        String json = ferramentas.executar("buscar_contatos", Map.of("unidade", "sao bento"), USUARIO);
        assertTrue(json.contains("Link fora em USF SÃO BENTO"), json);
        ArgumentCaptor<TelefoneVOs.PaginaReq> req = ArgumentCaptor.forClass(TelefoneVOs.PaginaReq.class);
        verify(telefoneService).pagina(req.capture(), eq(USUARIO));
        assertEquals(1L, req.getValue().getUnidadeId());
    }

    @Test
    void unidadeNoAr_naoAvisa() {
        when(telefoneService.unidades()).thenReturn(List.of(unidade(2, "UPA CENTRAL", false)));
        lista(contato("Recepção", false));
        assertFalse(ferramentas.executar("buscar_contatos", Map.of("unidade", "upa"), USUARIO).contains("aviso"));
    }

    @Test
    void unidadeInexistente_explica() {
        when(telefoneService.unidades()).thenReturn(List.of(unidade(2, "UPA CENTRAL", false)));
        assertTrue(ferramentas.executar("buscar_contatos", Map.of("unidade", "cras"), USUARIO).contains("nenhuma unidade"));
        verify(telefoneService, never()).pagina(any(), anyLong());
    }

    @Test
    void muitasUnidades_pedeParaEscolher() {
        when(telefoneService.unidades()).thenReturn(List.of(unidade(1, "UPA 1", false), unidade(2, "UPA 2", false),
                unidade(3, "UPA 3", false), unidade(4, "UPA 4", false), unidade(5, "UPA 5", false), unidade(6, "UPA 6", false)));
        String json = ferramentas.executar("buscar_contatos", Map.of("unidade", "upa"), USUARIO);
        assertTrue(json.contains("escolher") && json.contains("UPA 6"), json);
        verify(telefoneService, never()).pagina(any(), anyLong());
    }

    @Test
    void emergenciaVemPrimeiro_eFiltroChegaNaConsulta() {
        lista(contato("Recepção", false), contato("Gerente", true));
        String json = ferramentas.executar("buscar_contatos", Map.of("busca", "upa", "so_emergencia", true), USUARIO);
        assertTrue(json.indexOf("Gerente") < json.indexOf("Recepção"), json);
        ArgumentCaptor<TelefoneVOs.PaginaReq> req = ArgumentCaptor.forClass(TelefoneVOs.PaginaReq.class);
        verify(telefoneService).pagina(req.capture(), eq(USUARIO));
        assertEquals(Boolean.TRUE, req.getValue().getEmergencia());
    }

}
