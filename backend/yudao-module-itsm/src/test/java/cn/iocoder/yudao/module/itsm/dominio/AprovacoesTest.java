package cn.iocoder.yudao.module.itsm.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.iocoder.yudao.module.itsm.dominio.Aprovacoes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AprovacoesTest {

    @Test
    void situacaoGlobal() {
        assertEquals(NENHUMA, global(List.of()));
        assertEquals(AGUARDANDO, global(List.of(AGUARDANDO)));
        assertEquals(AGUARDANDO, global(List.of(APROVADA, AGUARDANDO)), "falta um aprovador");
        assertEquals(APROVADA, global(List.of(APROVADA, APROVADA)));
        assertEquals(RECUSADA, global(List.of(APROVADA, RECUSADA, AGUARDANDO)), "uma recusa basta");
    }

}
