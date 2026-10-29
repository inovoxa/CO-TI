package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** CO-TI: o login aceita os três formatos do Windows e autentica só a conta. */
class AuthLoginReqVOTest {

    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void contaDoLogin_tresFormatos() {
        assertEquals("joao.silva", AuthLoginReqVO.contaDoLogin("joao.silva"));
        assertEquals("joao.silva", AuthLoginReqVO.contaDoLogin("PMA\\joao.silva"));
        assertEquals("joao.silva", AuthLoginReqVO.contaDoLogin("joao.silva@pma.local"));
        assertEquals("joao.silva", AuthLoginReqVO.contaDoLogin("  PMA\\joao.silva  "));
        assertEquals("joao.silva", AuthLoginReqVO.contaDoLogin("PMA\\joao.silva@pma.local"));
        assertEquals("", AuthLoginReqVO.contaDoLogin("PMA\\"));
        assertNull(AuthLoginReqVO.contaDoLogin(null));
    }

    @Test
    void json_chegaNormalizadoEValido() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String login : new String[]{"PMA\\\\joao.silva", "joao.silva@pma.local", "joao.silva"}) {
            AuthLoginReqVO r = json.readValue("{\"username\":\"" + login + "\",\"password\":\"x\"}", AuthLoginReqVO.class);
            assertEquals("joao.silva", r.getUsername(), login);
            assertTrue(VALIDADOR.validateProperty(r, "username").isEmpty(), login);
        }
    }

    @Test
    void soDominio_continuaInvalido() {
        AuthLoginReqVO r = new AuthLoginReqVO();
        r.setUsername("PMA\\");
        assertFalse(VALIDADOR.validateProperty(r, "username").isEmpty());
    }

}
