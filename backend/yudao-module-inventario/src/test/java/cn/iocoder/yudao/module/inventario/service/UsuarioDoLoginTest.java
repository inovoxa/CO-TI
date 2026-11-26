package cn.iocoder.yudao.module.inventario.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UsuarioDoLoginTest {

    @Test
    void loginSemDominioEmMinusculas() {
        assertEquals("maria.souza", UsuarioDoLogin.limpo("PMA\\Maria.Souza"));
        assertEquals("maria", UsuarioDoLogin.limpo(" maria@pma.local "));
        assertEquals("joao", UsuarioDoLogin.limpo("JOAO"));
        assertNull(UsuarioDoLogin.limpo("PMA\\"));
        assertNull(UsuarioDoLogin.limpo(null));
    }

}
