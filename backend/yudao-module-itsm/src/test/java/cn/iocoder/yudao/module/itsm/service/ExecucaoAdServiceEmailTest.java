package cn.iocoder.yudao.module.itsm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecucaoAdServiceEmailTest {

    @Test
    void mascaraOEmailDeEntregaDaSenha() {
        assertEquals("m••••@gmail.com", ExecucaoAdService.mascararEmail(" maria@gmail.com "));
        assertEquals("•••@x.com", ExecucaoAdService.mascararEmail("a@x.com"));
        assertEquals("•••", ExecucaoAdService.mascararEmail(null));
    }

}
