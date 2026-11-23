package cn.iocoder.yudao.module.monitoramento.dominio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Unidade sugerida pelo nome do monitor, com nomes reais de unidades de uma prefeitura. */
class SugestaoUnidadeTest {

    private static final String[] NOMES = {"PSF LARANJEIRAS II", "CMS LARANJEIRAS I", "PSF MARIA LUIZA", "CRASS MARIALUIZA",
            "CER ELOA", "CER ADELINA AMARAL", "CER CARMELITA I", "CER CARMELITA II", "CER JUDITH DE BARROS BATELLI",
            "CENTRO ARTES OFICIOS JUDIT", "CMS SANTA LUCIA I", "CMS SANTA LUCIA II", "CMS SANTA LUCIA III", "CMS JD AMÉRICA",
            "CER PE. MARIO CAVARETTI FILHO", "CER EUGENIO TROVATI", "EMEF EUGENIO TROVATI", "SUB PREFEITURA",
            "SUBPREFEITURA BUENO", "PSF BUENO", "PEDÁGIO DE BUENO SW01", "SESI SERVICOS", "SECRETARIA DA SAÚDE SW01",
            "SEC CULTURA", "SEC BOTANICO", "SAMU", "ASSISTENCIA SOCIAL SW04", "PACO MUNICIPAL 1ANDAR", "PACO MUNICIPAL 2ANDAR",
            "PACO MUNICIPAL 3ANDAR", "PACO MUNICIPAL 6ANDAR", "PACO MUNICIPAL TERREO", "BIBLIOTECA MUNICIPAL",
            "TEATRO MUNICIPAL SW01", "CER PREFEITO CLODOALDO MEDINA"};

    private final SugestaoUnidade s;

    SugestaoUnidadeTest() {
        List<SugestaoUnidade.Unidade> l = new ArrayList<>();
        for (int i = 0; i < NOMES.length; i++) {
            l.add(new SugestaoUnidade.Unidade((long) i, NOMES[i]));
        }
        s = new SugestaoUnidade(l);
    }

    private String sugestao(String monitor) {
        Long id = s.sugerir(monitor);
        return id == null ? null : NOMES[id.intValue()];
    }

    @Test
    void exemplosPedidos() {
        assertEquals("PSF LARANJEIRAS II", sugestao("PSF LARANJEIRAS II"));
        assertEquals("PSF MARIA LUIZA", sugestao("PSF MARIA LUIZA (172.19.21.0)"));
        assertEquals("CER ELOA", sugestao("CER ELOA DO VALLE QUADROS (172.17.76.0)"));
        assertEquals("PACO MUNICIPAL 6ANDAR", sugestao("CÂMERA 01 - 6 ANDAR (172.21.6.40)"));
    }

    @Test
    void variacoesDeEscrita() {
        assertEquals("CER ADELINA AMARAL", sugestao("CER ADELINA LEITE AMARAL (172.17.34.0)"));
        assertEquals("CRASS MARIALUIZA", sugestao("CRAS MARIA LUIZA (172.20.10.0)"), "junta palavras e aceita um erro");
        assertEquals("CMS JD AMÉRICA", sugestao("CMS Jardim America"));
        assertEquals("CER EUGENIO TROVATI", sugestao("CER EUGENIO TROVATTI (172.17.90.0)"));
        assertEquals("EMEF EUGENIO TROVATI", sugestao("EMEF EUGENIO TROVATTI"));
        assertEquals("CER PE. MARIO CAVARETTI FILHO", sugestao("CER PE. MARIO CAVARETTI (172.17.126.0) - RP"));
        assertEquals("SEC CULTURA", sugestao("SECRETARIA DA CULTURA(172.20.31.0)"));
        assertEquals("PACO MUNICIPAL 3ANDAR", sugestao("Switch Paço 3º andar"));
        assertEquals("ASSISTENCIA SOCIAL SW04", sugestao("ASSISTENCIA SOCIAL SW01 (172.20.36.0)"), "SW não decide");
        assertEquals("SAMU", sugestao("VOIP SAMU - 172.19.48.126 - MEDICOS 204"));
        assertEquals("CER JUDITH DE BARROS BATELLI", sugestao("CER JUDITH (172.17.122.0)"), "forma curta de uma unidade só");
    }

    @Test
    void numeroDaUnidadeDecide() {
        assertEquals("CER CARMELITA II", sugestao("CER CARMELITA GARCEZ II (172.17.48.0)"));
        assertEquals("CER CARMELITA I", sugestao("CER CARMELITA GARCEZ I (172.17.44.0)"));
        assertEquals("CMS SANTA LUCIA II", sugestao("CMS SANTA LUCIA II"));
        assertEquals("PACO MUNICIPAL 2ANDAR", sugestao("ROTEADOR 2 ANDAR (172.21.2.15)"));
    }

    @Test
    void desempataPeloQueExplicaMaisDoNome() {
        assertEquals("SUBPREFEITURA BUENO", sugestao("SUBPREFEITURA BUENO (172.20.27.0)"));
    }

    @Test
    void naDuvidaFicaParaEscolherAMao() {
        assertNull(sugestao("CMS SANTA LUCIA"), "I, II ou III");
        assertNull(sugestao("Serviços WEB80"));
        assertNull(sugestao("SITE PREFEITURA"));
        assertNull(sugestao("SECRETARIA DE EDUCAÇÃO (10.0.8.0)"));
        assertNull(sugestao("ESTADIO MUNICIPAL BOTANICO"));
        assertNull(sugestao("UBIQUITI-PREFEITO"));
        assertNull(sugestao("FORTIGATE HA"));
        assertNull(sugestao("CÂMERA 07 - ELEVADOR (172.21.0.51)"));
        assertNull(sugestao(""));
        assertNull(sugestao(null));
        assertNull(new SugestaoUnidade(List.of()).sugerir("PSF BUENO"));
    }

    @Test
    void palavras() {
        assertEquals(List.of("CAMERA", "01", "6ANDAR"), SugestaoUnidade.palavras("CÂMERA 01 - 6 ANDAR (172.21.6.40)"));
    }

}
