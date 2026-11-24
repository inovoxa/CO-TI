package cn.iocoder.yudao.module.identidade.service;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A lista passa pelo escopo de dados (JSqlParser reescreve o SQL): filtros e ordenação precisam ser legíveis por ele. */
class UsuarioGestaoServiceTest {

    @Test
    void filtrosEOrdensSaoLidosPeloJSqlParser() {
        String base = "SELECT id FROM system_users WHERE deleted = 0"
                + " AND EXISTS (SELECT 1 FROM system_user_role r WHERE r.user_id = system_users.id AND r.deleted = 0 AND r.role_id = 1)"
                + " AND NOT EXISTS (SELECT 1 FROM identidade.mfa_totp m WHERE m.user_id = system_users.id AND m.deleted = 0 AND m.ativo)"
                + " AND system_users.id IN (" + UsuarioGestaoService.SQL_PRIVILEGIADOS + ")"
                + " AND " + UsuarioGestaoService.SQL_UNIDADE + " IN (1, 2) AND " + UsuarioGestaoService.SQL_UNIDADE + " IS NULL ";
        for (String ordem : UsuarioGestaoService.ORDENS.keySet()) {
            String sql = base + UsuarioGestaoService.ordenacao(ordem, "desc");
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql), ordem);
        }
    }

    @Test
    void ordemDesconhecidaViraNome() {
        assertEquals("ORDER BY lower(nickname) ASC NULLS LAST, id", UsuarioGestaoService.ordenacao("x; drop", null));
        assertTrue(UsuarioGestaoService.SQL_PRIVILEGIADOS.contains("'system:permission:assign-user-role'"));
    }

}
