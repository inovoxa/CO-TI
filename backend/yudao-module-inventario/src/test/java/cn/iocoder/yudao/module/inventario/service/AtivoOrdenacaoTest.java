package cn.iocoder.yudao.module.inventario.service;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A ordenação vai no SQL que o escopo de dados (JSqlParser) reescreve: toda expressão precisa ser legível por ele. */
class AtivoOrdenacaoTest {

    @Test
    void todasAsOrdensSaoLidasPeloJSqlParser() {
        for (String ordem : AtivoService.ORDENS.keySet()) {
            String sql = "SELECT id FROM cmdb.ativo WHERE deleted = 0 " + AtivoService.ordenacao(ordem, "desc");
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql), ordem);
        }
    }

    @Test
    void ordemDesconhecidaViraNomeENuncaEntraNoSql() {
        assertEquals("ORDER BY lower(nome) ASC NULLS LAST, id", AtivoService.ordenacao("1; DROP TABLE x", "qualquer"));
        assertTrue(AtivoService.ordenacao("contato", "DESC").startsWith("ORDER BY ultimo_contato DESC"));
    }

    @Test
    void consultasDoPainelSaoLidasPeloJSqlParser() {
        String[] exprs = {
                "coalesce((SELECT d.name FROM system_dept d WHERE d.id = a.dept_id), 'Sem unidade')",
                "CASE WHEN a.memoria_mb IS NULL THEN 'Não informado' WHEN a.memoria_mb < 3584 THEN 'Até 3 GB' ELSE '32 GB ou mais' END",
                "CASE WHEN a.ultimo_contato IS NULL THEN 'Nunca' WHEN a.ultimo_contato >= now() - interval '1 day' THEN 'Últimas 24 h' ELSE 'x' END"};
        for (String e : exprs) {
            String sql = "SELECT " + e + " AS valor, count(*) AS total FROM cmdb.ativo a WHERE a.deleted = 0 AND a.situacao <> 'baixado'"
                    + " AND a.tipo IN ('desktop', 'notebook') GROUP BY 1 ORDER BY total DESC LIMIT 10";
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql), e);
        }
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse("SELECT to_char(date_trunc('month', coalesce(a.primeiro_inventario, a.create_time)), 'YYYY-MM') AS valor,"
                + " count(*) AS total FROM cmdb.ativo a WHERE a.deleted = 0 AND coalesce(a.primeiro_inventario, a.create_time) >= ? GROUP BY 1 ORDER BY 1"));
    }

}
