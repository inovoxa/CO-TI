package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CatalogoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.CategoriaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.FormularioDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.FormularioMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** O arquivo do Exportar tem de voltar igual pelo Importar, em outra instalação. */
@ExtendWith(MockitoExtension.class)
class FormularioExportacaoTest {

    private static final String DEFINICAO =
            "{\"secoes\":[{\"nome\":\"Dados\",\"perguntas\":[{\"name\":\"nome_completo\",\"label\":\"Nome\",\"type\":\"text\",\"required\":true}]}]}";

    @Mock
    private FormularioMapper formularioMapper;
    @Mock
    private CadastroService cadastros;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private AprovacaoService aprovacaoService;
    @InjectMocks
    private FormularioService service;

    private CategoriaDO categoria;

    @BeforeEach
    void preparar() {
        categoria = new CategoriaDO();
        categoria.setId(7L);
        categoria.setNome("VPN");
        categoria.setNomeCompleto("Redes > Acesso > VPN");
        lenient().when(cadastros.listarCategorias(false, false)).thenReturn(List.of(categoria));
    }

    private FormularioDO.FormularioDOBuilder base(String codigo) {
        return FormularioDO.builder().id(1L).codigo(codigo).nome("Pedido " + codigo).grupo("Acessos").tipo(2).urgencia(3)
                .impacto(3).definicao(DEFINICAO).aprovacao("nenhuma").destaque(false).ativo(true).ordem(4);
    }

    private String exportado(FormularioDO... formularios) {
        when(formularioMapper.selectList(ArgumentMatchers.<Wrapper<FormularioDO>>any())).thenReturn(List.of(formularios));
        return JsonUtils.toJsonString(service.exportar());
    }

    private FormularioDO gravado() {
        ArgumentCaptor<FormularioDO> c = ArgumentCaptor.forClass(FormularioDO.class);
        verify(formularioMapper).insert(c.capture());
        return c.getValue();
    }

    private void comAprovador(long idOrigem, String login) {
        AdminUserRespDTO u = new AdminUserRespDTO();
        u.setId(idOrigem);
        u.setUsername(login);
        when(adminUserApi.getUserMap(any())).thenReturn(Map.of(idOrigem, u));
    }

    @Test
    void preservaInativoCamposECategoriaProfunda() {
        String json = exportado(base("1.1").categoriaId(7L).ativo(false).tituloModelo("Pedido: {nome_completo}").build());

        CatalogoVOs.ImportacaoResp r = service.importar(json, true);

        assertEquals(1, r.getCriados());
        assertEquals(List.of(), r.getAvisos());
        FormularioDO f = gravado();
        assertEquals("1.1", f.getCodigo());
        assertFalse(f.getAtivo());
        assertEquals(7L, f.getCategoriaId());
        assertEquals(4, f.getOrdem());
        assertEquals("Pedido: {nome_completo}", f.getTituloModelo());
        assertTrue(f.getDefinicao().contains("nome_completo"));
        verify(cadastros, never()).salvarCategoria(any());
    }

    @Test
    void formularioSemCategoriaContinuaSem() {
        String json = exportado(base("1.2").build());

        service.importar(json, true);

        assertNull(gravado().getCategoriaId());
        verify(cadastros, never()).salvarCategoria(any());
    }

    @Test
    void aprovadorFixoEResolvidoPeloLoginNoDestino() {
        comAprovador(5L, "maria");
        String json = exportado(base("1.3").aprovacao("fixo").aprovadorId(5L).build());
        AdminUserRespDTO destino = new AdminUserRespDTO();
        destino.setId(99L);
        when(adminUserApi.getUserByUsername("maria")).thenReturn(destino);
        when(aprovacaoService.podeAprovar(99L)).thenReturn(true);

        CatalogoVOs.ImportacaoResp r = service.importar(json, true);

        assertEquals(List.of(), r.getAvisos());
        FormularioDO f = gravado();
        assertEquals("fixo", f.getAprovacao());
        assertEquals(99L, f.getAprovadorId());
        assertTrue(f.getAtivo());
    }

    @Test
    void aprovadorInexistenteEntraInativoEComAviso() {
        comAprovador(5L, "maria");
        String json = exportado(base("1.4").aprovacao("fixo").aprovadorId(5L).build());

        CatalogoVOs.ImportacaoResp r = service.importar(json, true);

        assertEquals(1, r.getCriados());
        assertEquals(1, r.getAvisos().size());
        FormularioDO f = gravado();
        assertEquals("nenhuma", f.getAprovacao());
        assertFalse(f.getAtivo());
    }
}
