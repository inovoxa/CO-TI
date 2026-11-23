package cn.iocoder.yudao.module.monitoramento.service.verificador;

import cn.iocoder.yudao.module.monitoramento.dominio.Estados;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Leitura do /metrics do Uptime Kuma e o estado espelhado. */
class KumaAoVivoTest {

    /** Como o Kuma 2.5.7 escreve (prom-client), com rótulo de etiqueta e um nome com aspas e barra. */
    private static final String METRICAS_2X = """
            # HELP monitor_cert_days_remaining The number of days remaining until the certificate expires
            # TYPE monitor_cert_days_remaining gauge
            monitor_cert_days_remaining{monitor_id="3",monitor_name="Portal",monitor_type="http",monitor_url="https://portal.exemplo.gov.br",monitor_hostname="null",monitor_port="null"} 41
            # HELP monitor_cert_is_valid Is the certificate still valid? (1 = Yes, 0= No)
            # TYPE monitor_cert_is_valid gauge
            monitor_cert_is_valid{monitor_id="3",monitor_name="Portal",monitor_type="http",monitor_url="https://portal.exemplo.gov.br",monitor_hostname="null",monitor_port="null"} 1
            # HELP monitor_response_time Monitor Response Time (ms)
            # TYPE monitor_response_time gauge
            monitor_response_time{monitor_id="3",monitor_name="Portal",monitor_type="http",monitor_url="https://portal.exemplo.gov.br",monitor_hostname="null",monitor_port="null"} 187
            monitor_response_time{monitor_id="7",monitor_name="Link \\"Centro\\" 1\\\\2",monitor_type="ping",monitor_url="https://",monitor_hostname="10.1.0.1",monitor_port="null",Filial="centro"} -1
            monitor_response_time{monitor_id="9",monitor_name="Banco",monitor_type="port",monitor_url="https://",monitor_hostname="db.local",monitor_port="5432"} 4.6
            # HELP monitor_status Monitor Status (1 = UP, 0= DOWN, 2= PENDING, 3= MAINTENANCE)
            # TYPE monitor_status gauge
            monitor_status{monitor_id="3",monitor_name="Portal",monitor_type="http",monitor_url="https://portal.exemplo.gov.br",monitor_hostname="null",monitor_port="null"} 1
            monitor_status{monitor_id="7",monitor_name="Link \\"Centro\\" 1\\\\2",monitor_type="ping",monitor_url="https://",monitor_hostname="10.1.0.1",monitor_port="null",Filial="centro"} 0
            monitor_status{monitor_id="9",monitor_name="Banco",monitor_type="port",monitor_url="https://",monitor_hostname="db.local",monitor_port="5432"} 2
            # HELP monitor_uptime_ratio Uptime ratio
            monitor_uptime_ratio{monitor_id="3",monitor_name="Portal",window="1d"} 0.998
            process_cpu_user_seconds_total 12.5
            """;

    @Test
    void leOsMonitoresDoKuma2() {
        List<ClienteKuma.MonitorKuma> l = ClienteKuma.interpretar(METRICAS_2X);
        assertEquals(3, l.size());

        ClienteKuma.MonitorKuma portal = ClienteKuma.achar(l, "3");
        assertNotNull(portal);
        assertEquals("Portal", portal.nome());
        assertEquals(Estados.NO_AR, portal.estado());
        assertEquals(187, portal.latenciaMs());
        assertEquals(41, portal.certDias());
        assertEquals("https://portal.exemplo.gov.br", portal.alvo());

        ClienteKuma.MonitorKuma link = ClienteKuma.achar(l, "7");
        assertEquals("Link \"Centro\" 1\\2", link.nome());
        assertEquals(Estados.FORA, link.estado());
        assertNull(link.latenciaMs(), "-1 é sem resposta");
        assertEquals("10.1.0.1", link.alvo(), "ping não tem URL: vale o host");

        ClienteKuma.MonitorKuma banco = ClienteKuma.achar(l, "9");
        assertEquals(Estados.PENDENTE, banco.estado());
        assertEquals(5, banco.latenciaMs());
        assertEquals("db.local:5432", banco.alvo());
        assertNull(banco.certDias());
    }

    @Test
    void kuma1SemIdAchaPeloNome() {
        String m = """
                monitor_status{monitor_name="Site",monitor_type="http",monitor_url="https://a.b",monitor_hostname="null",monitor_port="null"} 3
                """;
        List<ClienteKuma.MonitorKuma> l = ClienteKuma.interpretar(m);
        assertEquals(1, l.size());
        assertNull(l.get(0).id());
        assertEquals(Estados.MANUTENCAO, ClienteKuma.achar(l, "site").estado());
        assertNull(ClienteKuma.achar(l, "1"));
        assertNull(ClienteKuma.achar(l, " "));
    }

    @Test
    void validadeDoCertificadoNaoOscila() {
        LocalDateTime guardada = LocalDate.now().plusDays(30).atStartOfDay();
        assertEquals(guardada, ClienteKuma.validade(guardada, 29), "um dia a menos pelo arredondamento: mesma data");
        assertEquals(guardada, ClienteKuma.validade(guardada, 31));
        assertEquals(LocalDate.now().plusDays(90).atStartOfDay(), ClienteKuma.validade(guardada, 90), "renovou: data nova");
        assertEquals(LocalDate.now().plusDays(10).atStartOfDay(), ClienteKuma.validade(null, 10));
    }

    @Test
    void espelhaOEstadoDoKumaSemTentativasDaqui() {
        // fora no Kuma cai na hora (o Kuma já tentou) e avisa
        Estados.Transicao t = Estados.espelhar(Estados.NO_AR, 0, Estados.FORA, 0);
        assertEquals(Estados.FORA, t.estado());
        assertTrue(t.avisar());
        // pendente fica pendente, sem aviso, por quantas leituras forem
        t = Estados.espelhar(Estados.NO_AR, 0, Estados.PENDENTE, 0);
        assertEquals(Estados.PENDENTE, t.estado());
        assertFalse(t.avisar());
        t = Estados.espelhar(Estados.PENDENTE, 0, Estados.PENDENTE, 0);
        assertEquals(Estados.PENDENTE, t.estado());
        // pendente → fora avisa; fora → no ar avisa a volta
        assertTrue(Estados.espelhar(Estados.PENDENTE, 0, Estados.FORA, 0).avisar());
        t = Estados.espelhar(Estados.FORA, 0, Estados.NO_AR, 0);
        assertEquals(Estados.NO_AR, t.estado());
        assertTrue(t.avisar());
        // manutenção no Kuma é manutenção aqui, sem aviso
        t = Estados.espelhar(Estados.NO_AR, 0, Estados.MANUTENCAO, 0);
        assertEquals(Estados.MANUTENCAO, t.estado());
        assertFalse(t.avisar());
        // reaviso continua valendo enquanto o Kuma diz fora
        t = Estados.espelhar(Estados.FORA, 1, Estados.FORA, 2);
        assertTrue(t.reaviso());
    }

    @Test
    void resultadoEspelhoGuardaOEstado() {
        Resultado r = Resultado.espelho(Estados.PENDENTE, null, "x").comCertificado(5, LocalDateTime.now());
        assertEquals(Estados.PENDENTE, r.estadoExterno());
        assertFalse(r.ok());
        assertNull(Resultado.ok(1, "y").estadoExterno());
    }

}
