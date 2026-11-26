package cn.iocoder.yudao.module.identidade.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanilhasSoAdministradorTest {

    @Test
    void soOsCaminhosDePlanilha() {
        for (String s : new String[]{"/admin-api/system/user/export-excel", "/admin-api/inventario/ativo/exportar-excel",
                "/admin-api/monitoramento/unidade/exportar", "/admin-api/monitoramento/unidade/importar/analisar",
                "/admin-api/system/user/import", "/admin-api/system/user/get-import-template", "/admin-api/itsm/telefone/importar"}) {
            assertTrue(PlanilhasSoAdministrador.ehPlanilha(s), s);
        }
        for (String s : new String[]{"/admin-api/itsm/importacao/iniciar", "/admin-api/inventario/ativo/page",
                "/admin-api/system/user/importante", "/admin-api/inventario/ativo/inconsistencias", null}) {
            assertFalse(PlanilhasSoAdministrador.ehPlanilha(s), String.valueOf(s));
        }
    }

}
