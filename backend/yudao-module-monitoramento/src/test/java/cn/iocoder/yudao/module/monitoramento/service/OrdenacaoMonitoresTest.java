package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrdenacaoMonitoresTest {

    private static final Map<Long, String> UNIDADES = Map.of(10L, "Água Branca", 20L, "Centro");

    private static MonitorDO.MonitorDOBuilder base(long id, String nome) {
        return MonitorDO.builder().id(id).nome(nome);
    }

    private static List<String> ordenar(String campo, boolean desc, Map<Long, Double> uptime, MonitorDO... ms) {
        Comparator<MonitorDO> c = OrdenacaoMonitores.comparador(campo, desc, UNIDADES, uptime);
        List<MonitorDO> l = new ArrayList<>(Arrays.asList(ms));
        l.sort(c);
        return l.stream().map(MonitorDO::getNome).toList();
    }

    @Test
    void nomeIgnoraAcentoEMaiuscula() {
        MonitorDO[] ms = {base(1, "Zeta").build(), base(2, "água").build(), base(3, "Beta").build()};
        assertEquals(List.of("água", "Beta", "Zeta"), ordenar("nome", false, Map.of(), ms));
        assertEquals(List.of("Zeta", "Beta", "água"), ordenar("nome", true, Map.of(), ms));
    }

    @Test
    void certificadoSemDadoFicaNoFimNosDoisSentidos() {
        MonitorDO[] ms = {base(1, "sem").build(), base(2, "longe").certDias(100).build(), base(3, "perto").certDias(8).build()};
        assertEquals(List.of("perto", "longe", "sem"), ordenar("certificado", false, Map.of(), ms));
        assertEquals(List.of("longe", "perto", "sem"), ordenar("certificado", true, Map.of(), ms));
    }

    @Test
    void situacaoAgrupaEDesempataPeloNome() {
        MonitorDO[] ms = {base(1, "B no ar").ordem(2).build(), base(2, "Z fora").ordem(0).build(), base(3, "A no ar").ordem(2).build(),
                base(4, "A fora").ordem(0).build(), base(5, "pendente").ordem(1).build()};
        assertEquals(List.of("A fora", "Z fora", "pendente", "A no ar", "B no ar"), ordenar("situacao", false, Map.of(), ms));
    }

    @Test
    void unidadePeloNomeESemUnidadeNoFim() {
        MonitorDO[] ms = {base(1, "sem").build(), base(2, "centro").unidadeId(20L).build(), base(3, "agua").unidadeId(10L).build()};
        assertEquals(List.of("agua", "centro", "sem"), ordenar("unidade", false, Map.of(), ms));
        assertEquals(List.of("centro", "agua", "sem"), ordenar("unidade", true, Map.of(), ms));
    }

    @Test
    void disponibilidadeEResposta() {
        Map<Long, Double> uptime = new HashMap<>(Map.of(1L, 100.0, 2L, 73.5));
        MonitorDO[] ms = {base(1, "cem").ultimaLatenciaMs(50).build(), base(2, "baixa").ultimaLatenciaMs(900).build(), base(3, "nova").build()};
        assertEquals(List.of("baixa", "cem", "nova"), ordenar(OrdenacaoMonitores.UPTIME_24H, false, uptime, ms));
        assertEquals(List.of("cem", "baixa", "nova"), ordenar(OrdenacaoMonitores.UPTIME_30D, true, uptime, ms));
        assertEquals(List.of("cem", "baixa", "nova"), ordenar("resposta", false, Map.of(), ms));
    }

    @Test
    void soAceitaCamposConhecidos() {
        assertTrue(OrdenacaoMonitores.aceita("certificado"));
        assertFalse(OrdenacaoMonitores.aceita("senha; drop table"));
        assertFalse(OrdenacaoMonitores.aceita(""));
    }

    @Test
    void paginaRecortaESemLimiteDevolveTudo() {
        List<Integer> l = List.of(1, 2, 3, 4, 5);
        assertEquals(List.of(3, 4), OrdenacaoMonitores.pagina(l, 2, 2));
        assertEquals(List.of(5), OrdenacaoMonitores.pagina(l, 3, 2));
        assertEquals(List.of(), OrdenacaoMonitores.pagina(l, 9, 2));
        assertEquals(l, OrdenacaoMonitores.pagina(l, 1, PageParam.PAGE_SIZE_NONE));
    }

}
