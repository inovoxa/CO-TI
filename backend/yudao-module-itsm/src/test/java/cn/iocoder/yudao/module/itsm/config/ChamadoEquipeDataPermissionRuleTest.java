package cn.iocoder.yudao.module.itsm.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChamadoEquipeDataPermissionRuleTest {

    @Test
    void semEquipeSoPeloProprioUsuario() {
        String s = ChamadoEquipeDataPermissionRule.modelo(7L, List.of());
        assertTrue(s.contains("a.user_id = 7"));
        assertFalse(s.contains("equipe_id"));
        assertTrue(s.contains("registrado_por = 7"));
    }

    @Test
    void comEquipesFiltraPeloAtorTecnico() {
        String s = ChamadoEquipeDataPermissionRule.modelo(7L, List.of(3L, 4L));
        assertTrue(s.contains("a.equipe_id IN (3,4)"));
        assertTrue(s.contains("a.tipo = 2"));
    }

}
