package cn.iocoder.yudao.module.logs.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsultaTest {

    @Test
    void vazia_e_verdadeira() {
        assertEquals("TRUE", Consulta.traduzir("  ").condicao());
    }

    @Test
    void campo_igual_usa_o_indice_json() {
        Consulta.Sql s = Consulta.traduzir("event_id:4624");
        assertEquals("COALESCE(campos @> CAST(? AS jsonb), FALSE)", s.condicao());
        assertEquals(List.of("{\"event_id\":\"4624\"}"), s.parametros());
    }

    @Test
    void and_implicito_or_e_aspas() {
        Consulta.Sql s = Consulta.traduzir("event_id:4624 usuario:\"ANONYMOUS LOGON\" OR origem:DC01");
        assertEquals("((COALESCE(campos @> CAST(? AS jsonb), FALSE) AND COALESCE(campos @> CAST(? AS jsonb), FALSE))"
                + " OR COALESCE(origem = ?, FALSE))", s.condicao());
        assertEquals(List.of("{\"event_id\":\"4624\"}", "{\"usuario\":\"ANONYMOUS LOGON\"}", "DC01"), s.parametros());
    }

    @Test
    void negacao_curinga_e_parenteses() {
        Consulta.Sql s = Consulta.traduzir("-usuario:*$ AND (hostname:*youtube* OR NOT action:blocked)");
        assertTrue(s.condicao().startsWith("(NOT COALESCE(campos->>? ILIKE ?, FALSE) AND ("));
        assertEquals(List.of("usuario", "%$", "hostname", "%youtube%", "{\"action\":\"blocked\"}"), s.parametros());
    }

    @Test
    void comparacao_numerica() {
        assertEquals("COALESCE(nivel <= ?, FALSE)", Consulta.traduzir("nivel:<=3").condicao());
        Consulta.Sql s = Consulta.traduzir("sentbyte:>1000");
        assertTrue(s.condicao().contains("::numeric END) > ?"));
        assertEquals(List.of("sentbyte", "sentbyte", 1000.0), s.parametros());
    }

    @Test
    void texto_livre_escapa_curingas_do_like() {
        Consulta.Sql s = Consulta.traduzir("100%_ok");
        assertEquals("mensagem ILIKE ?", s.condicao());
        assertEquals(List.of("%100\\%\\_ok%"), s.parametros());
    }

    @Test
    void existe() {
        Consulta.Sql s = Consulta.traduzir("_exists_:catdesc");
        assertEquals("COALESCE(jsonb_exists(campos, ?), FALSE)", s.condicao());
    }

    @Test
    void erros_explicados() {
        assertThrows(IllegalArgumentException.class, () -> Consulta.traduzir("(a OR b"));
        assertThrows(IllegalArgumentException.class, () -> Consulta.traduzir("usuario:\"sem fim"));
        assertThrows(IllegalArgumentException.class, () -> Consulta.traduzir("a OR"));
        assertThrows(IllegalArgumentException.class, () -> Consulta.traduzir("x';drop:1"));
        assertThrows(IllegalArgumentException.class, () -> Consulta.traduzir("nivel:alto"));
    }

    @Test
    void campo_do_ranking() {
        assertEquals("origem", Consulta.campo("source").condicao());
        assertEquals("campos->>?", Consulta.campo("hostname").condicao());
        assertThrows(IllegalArgumentException.class, () -> Consulta.campo("a b"));
    }

}
