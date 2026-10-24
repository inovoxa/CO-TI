package cn.iocoder.yudao.module.itsm.dominio;

import cn.iocoder.yudao.module.itsm.dominio.MotorRegras.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MotorRegrasTest {

    private static final Map<String, Object> FATOS = Map.of("titulo", "Impressora do RH não imprime", "categoria_id", 23L,
            "urgencia", 4, "dept_id", 105L, "descricao", "Atolou papel no 2º andar");

    private static Regra regra(long id, boolean todos, boolean parar, List<Criterio> c, Acao... a) {
        return new Regra(id, "R" + id, todos, parar, c, List.of(a));
    }

    @Test
    void operadoresDeTextoIgnoramMaiusculasEAcentos() {
        assertTrue(MotorRegras.verdadeiro(new Criterio("titulo", "contem", "NAO IMPRIME"), FATOS));
        assertTrue(MotorRegras.verdadeiro(new Criterio("titulo", "comeca", "impressora"), FATOS));
        assertTrue(MotorRegras.verdadeiro(new Criterio("titulo", "nao_contem", "rede"), FATOS));
        assertFalse(MotorRegras.verdadeiro(new Criterio("titulo", "contem", ""), FATOS), "contém vazio não casa");
        assertTrue(MotorRegras.verdadeiro(new Criterio("descricao", "regex", "\\d+º\\s+andar"), FATOS));
    }

    @Test
    void operadoresDeValor() {
        assertTrue(MotorRegras.verdadeiro(new Criterio("categoria_id", "igual", "23"), FATOS));
        assertTrue(MotorRegras.verdadeiro(new Criterio("categoria_id", "em", "15, 23, 40"), FATOS));
        assertTrue(MotorRegras.verdadeiro(new Criterio("urgencia", "maior", "3"), FATOS));
        assertFalse(MotorRegras.verdadeiro(new Criterio("urgencia", "menor", "3"), FATOS));
        assertTrue(MotorRegras.verdadeiro(new Criterio("origem_id", "igual", ""), FATOS), "fato ausente = vazio");
        assertFalse(MotorRegras.verdadeiro(new Criterio("urgencia", "maior", "abc"), FATOS));
    }

    @Test
    void combinacaoEOuEParar() {
        Regra e = regra(1, true, false, List.of(new Criterio("categoria_id", "igual", "23"),
                new Criterio("urgencia", "igual", "5")), new Acao("atribuir_equipe", "7"));
        Regra ou = regra(2, false, true, List.of(new Criterio("categoria_id", "igual", "99"),
                new Criterio("dept_id", "igual", "105")), new Acao("definir_prioridade", "5"));
        Regra depois = regra(3, true, false, List.of(new Criterio("titulo", "contem", "impressora")),
                new Acao("atribuir_tecnico", "42"));

        List<Disparo> d = MotorRegras.avaliar(List.of(e, ou, depois), FATOS);
        assertEquals(1, d.size(), "E não casou (urgência 4); OU casou e parou antes da regra 3");
        assertEquals("definir_prioridade", d.get(0).acao().acao());
        assertEquals(2L, d.get(0).regraId());
    }

    @Test
    void semCriterioNuncaCasa() {
        assertTrue(MotorRegras.avaliar(List.of(regra(1, true, false, List.of(), new Acao("atribuir_equipe", "1"))), FATOS).isEmpty());
    }

    @Test
    void regexInvalidaNaoQuebraEValidacaoAcusa() {
        assertFalse(MotorRegras.verdadeiro(new Criterio("titulo", "regex", "([a-z"), FATOS));
        List<String> erros = MotorRegras.validar(List.of(new Criterio("titulo", "regex", "([a-z"),
                new Criterio("senha", "igual", "x")), List.of(new Acao("apagar_tudo", "")));
        assertEquals(3, erros.size(), erros.toString());
    }

}
