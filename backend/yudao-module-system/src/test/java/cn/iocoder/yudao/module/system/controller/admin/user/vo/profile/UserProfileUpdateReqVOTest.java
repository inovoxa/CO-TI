package cn.iocoder.yudao.module.system.controller.admin.user.vo.profile;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CO-TI: a foto enviada fica no disco local, com endereço relativo, e precisa passar na validação. */
class UserProfileUpdateReqVOTest {

    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void foto_aceitaArmazenamentoLocalEEnderecoCompleto() {
        for (String foto : new String[]{"/admin-api/infra/file/50000/get/20261029/abc/avatar.png",
                "https://cdn.exemplo.gov.br/fotos/1.png", ""}) {
            assertTrue(valida(foto), foto);
        }
    }

    @Test
    void foto_recusaOutrosEnderecos() {
        for (String foto : new String[]{"javascript:alert(1)", "/admin-api/system/user/delete?id=1", "foto.png",
                "/admin-api/infra/file/50000/get/a b.png"}) {
            assertFalse(valida(foto), foto);
        }
    }

    private static boolean valida(String foto) {
        UserProfileUpdateReqVO r = new UserProfileUpdateReqVO();
        r.setAvatar(foto);
        return VALIDADOR.validateProperty(r, "avatar").isEmpty();
    }

}
