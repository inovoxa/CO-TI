package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.module.identidade.config.AdProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdProvisionamentoServiceTest {

    private static final Set<String> GRUPOS = Set.of(
            "CN=GG-TI-NOC,OU=Grupos,DC=empresa,DC=local",
            "CN=Todos,OU=Grupos,DC=empresa,DC=local");

    @Test
    void reconheceGrupoPeloCnOuPeloDn() {
        assertTrue(AdProvisionamentoService.pertenceAoGrupo(GRUPOS, "GG-TI-NOC"));
        assertTrue(AdProvisionamentoService.pertenceAoGrupo(GRUPOS, "gg-ti-noc"));
        assertTrue(AdProvisionamentoService.pertenceAoGrupo(GRUPOS, "CN=GG-TI-NOC,OU=Grupos,DC=empresa,DC=local"));
    }

    @Test
    void naoConfundePrefixoNemOu() {
        assertFalse(AdProvisionamentoService.pertenceAoGrupo(GRUPOS, "GG-TI"));
        assertFalse(AdProvisionamentoService.pertenceAoGrupo(GRUPOS, "Grupos"));
    }

    @Test
    void grupoDeAprovadoresEntraNoMapaDePerfis() {
        AdProperties p = new AdProperties();
        p.getGruposPerfis().put("GG-TI-NOC", "noc");
        p.setGrupoAprovadores(" GG-Aprovadores ; CN=GG-Diretores,OU=Grupos,DC=empresa,DC=local;");
        assertEquals(Map.of("GG-TI-NOC", "noc", "GG-Aprovadores", "aprovador_ad",
                "CN=GG-Diretores,OU=Grupos,DC=empresa,DC=local", "aprovador_ad"), p.getGruposPerfisEfetivos());
    }

    @Test
    void semGrupoDeAprovadoresOMapaFicaIgual() {
        AdProperties p = new AdProperties();
        assertEquals(Map.of(), p.getGruposPerfisEfetivos());
    }

}
