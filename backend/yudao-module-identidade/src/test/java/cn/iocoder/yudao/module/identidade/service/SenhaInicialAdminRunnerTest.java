package cn.iocoder.yudao.module.identidade.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SenhaInicialAdminRunnerTest {

    @Test
    void exigeSenhaInicialForte() {
        assertThrows(IllegalStateException.class, () -> SenhaInicialAdminRunner.validar(null));
        assertThrows(IllegalStateException.class, () -> SenhaInicialAdminRunner.validar("   "));
        assertThrows(IllegalStateException.class, () -> SenhaInicialAdminRunner.validar("curta"));
        assertThrows(IllegalStateException.class, () -> SenhaInicialAdminRunner.validar("admin123"));
        assertDoesNotThrow(() -> SenhaInicialAdminRunner.validar("uma-senha-longa-2026"));
    }

}
