package cn.iocoder.yudao.module.identidade.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

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

}
