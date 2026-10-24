package cn.iocoder.yudao.module.atendimento.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelevanciaTest {

    private static Relevancia.Candidato<String> c(String id, String titulo, String texto) {
        return new Relevancia.Candidato<>(id, titulo, texto);
    }

    @Test
    void termos() {
        assertEquals(List.of("config", "vpn", "sapie"), Relevancia.termos("Como configuro a VPN do Sapiens?"));
        assertEquals(List.of("acess"), Relevancia.termos("acesso"));
        assertEquals(List.of(), Relevancia.termos("oi, bom dia"));
    }

    @Test
    void termoRaroGanhaDoComumENoTituloValeMais() {
        var lista = List.of(
                c("fixado", "Como conectar na VPN", "Use o cliente."),
                c("config1", "Configuração da VPN", "Passo a passo da VPN."),
                c("config2", "Configuração da VPN (antiga)", "VPN legada."),
                c("sapiens-corpo", "Acesso remoto", "Para o Sapiens, use a VPN da prefeitura."),
                c("sapiens-titulo", "Sapiens fora da rede", "Ligue a VPN."));
        assertEquals(List.of("sapiens-titulo", "sapiens-corpo", "fixado"), Relevancia.ordenar("A VPN do Sapiens", lista, 3));
    }

    @Test
    void semTermosMantemAOrdemDaBusca() {
        var lista = List.of(c("a", "A", ""), c("b", "B", ""));
        assertEquals(List.of("a"), Relevancia.ordenar("oi", lista, 1));
    }

}
