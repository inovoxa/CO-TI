package cn.iocoder.yudao.module.assistente.service;

import cn.iocoder.yudao.module.logs.controller.admin.vo.LogsVOs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Banda nas ferramentas de logs do assistente: o modelo lê esses valores como vieram, então precisam estar certos. */
class FerramentasInternasBandaTest {

    @Test
    void bytes_emBase1024ComVirgula() {
        assertEquals("0 B", FerramentasInternas.bytes(0));
        assertEquals("1023 B", FerramentasInternas.bytes(1023));
        assertEquals("1,0 KB", FerramentasInternas.bytes(1024));
        assertEquals("1,5 KB", FerramentasInternas.bytes(1536));
        // 2550 MB do teste da UBS Vila Xavier = 2,5 GB
        assertEquals("2,5 GB", FerramentasInternas.bytes(2550L * 1024 * 1024));
        assertEquals("1,0 TB", FerramentasInternas.bytes(1L << 40));
    }

    @Test
    void banda_somaSoOsLogsDeSessao() {
        LogsVOs.Filtro f = filtro("hostname:*netflix*", null);
        FerramentasInternas.sessoesDeBanda(f, "bytes");
        assertEquals("type:traffic AND (hostname:*netflix*)", f.getConsulta());
        assertEquals("firewall", f.getFluxo());

        f = filtro(null, null);
        FerramentasInternas.sessoesDeBanda(f, "bytes");
        assertEquals("type:traffic", f.getConsulta());
    }

    @Test
    void banda_respeitaOTipoEscolhido() {
        // quem já escolheu o tipo (ou o subtipo) sabe o que quer somar
        for (String q : new String[]{"type:traffic AND dstintfrole:wan", "subtype:webfilter AND srcip:10.0.15.*"}) {
            LogsVOs.Filtro f = filtro(q, "firewall");
            FerramentasInternas.sessoesDeBanda(f, "bytes");
            assertEquals(q, f.getConsulta());
        }
    }

    @Test
    void contagem_semSomaNaoMexe() {
        LogsVOs.Filtro f = filtro("hostname:*youtube*", null);
        FerramentasInternas.sessoesDeBanda(f, null);
        assertEquals("hostname:*youtube*", f.getConsulta());
        assertNull(f.getFluxo());
    }

    private static LogsVOs.Filtro filtro(String consulta, String fluxo) {
        LogsVOs.Filtro f = new LogsVOs.Filtro();
        f.setConsulta(consulta);
        f.setFluxo(fluxo);
        return f;
    }

}
