package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LimpezaInventarioServiceTest {

    private static AtivoDO ativo(long id, String nome, String tipo, String serie, String uuid, String mac, int diasSemContato) {
        return AtivoDO.builder().id(id).nome(nome).tipo(tipo).fabricante("Dell").numeroSerie(serie).uuid(uuid).mac(mac)
                .ultimoContato(LocalDateTime.now().minusDays(diasSemContato)).build();
    }

    @Test
    void formatadaComOutroHardwareEMesmoNomeAgrupaComOMaisRecentePrimeiro() {
        List<AtivoDO> l = List.of(
                ativo(1, "PSF-RECEPCAO", "desktop", "ABC123", "uuid-1", "AA:BB:CC:00:00:01", 120),
                ativo(2, "psf-recepcao ", "desktop", "XYZ999", "uuid-2", "AA:BB:CC:00:00:02", 0),
                ativo(3, "CER-SALA1", "desktop", "QWE111", "uuid-3", "AA:BB:CC:00:00:03", 0));
        List<List<AtivoDO>> g = LimpezaInventarioService.agrupar(l, Set.of());
        assertEquals(1, g.size());
        assertEquals(List.of(2L, 1L), g.get(0).stream().map(AtivoDO::getId).toList(), "o que está mandando agora vem primeiro");
    }

    @Test
    void outroNomeMasMesmaSerieOuMacTambemAgrupa() {
        List<AtivoDO> l = List.of(
                ativo(1, "PC-ANTIGO", "desktop", "ABC123", null, null, 60),
                ativo(2, "PC-NOVO", "desktop", "ABC123", null, null, 0),
                ativo(3, "NOTE-A", "notebook", null, null, "AA:BB:CC:00:00:09", 40),
                ativo(4, "NOTE-B", "notebook", null, null, "aa-bb-cc-00-00-09", 1));
        assertEquals(2, LimpezaInventarioService.agrupar(l, Set.of()).size());
    }

    @Test
    void valoresGenericosNomeDeImpressoraEParesDiferentesNaoAgrupam() {
        List<AtivoDO> l = new ArrayList<>(List.of(
                ativo(1, "A", "desktop", "To Be Filled By O.E.M.", "00000000-0000-0000-0000-000000000000", "00:00:00:00:00:00", 0),
                ativo(2, "B", "desktop", "To Be Filled By O.E.M.", "00000000-0000-0000-0000-000000000000", "00:00:00:00:00:00", 0),
                ativo(3, "HP LaserJet", "impressora", null, null, null, 0),
                ativo(4, "HP LaserJet", "impressora", null, null, null, 0),
                ativo(5, "SALA-1", "desktop", null, null, null, 0),
                ativo(6, "SALA-1", "desktop", null, null, null, 0)));
        assertTrue(LimpezaInventarioService.agrupar(l, Set.of("5|6")).isEmpty());
        assertEquals(1, LimpezaInventarioService.agrupar(l, Set.of()).size());
    }

    @Test
    void nomeRepetidoEmMuitasMaquinasEhGenerico() {
        List<AtivoDO> l = new ArrayList<>();
        for (int i = 1; i <= LimpezaInventarioService.MAXIMO_POR_CHAVE + 1; i++) {
            l.add(ativo(i, "DESKTOP", "desktop", null, null, null, 0));
        }
        assertTrue(LimpezaInventarioService.agrupar(l, Set.of()).isEmpty());
    }

}
