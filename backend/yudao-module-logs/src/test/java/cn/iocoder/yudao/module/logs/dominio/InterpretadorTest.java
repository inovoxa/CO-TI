package cn.iocoder.yudao.module.logs.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class InterpretadorTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 27, 11, 30);

    @Test
    void forti_webfilter() {
        String linha = "<189>date=2026-10-27 time=11:19:03 devname=\"FGT-PMA-01\" devid=\"FG4H0FT924903004\" "
                + "eventtime=1793110743443570248 tz=\"-0300\" logid=\"0315012545\" type=\"utm\" subtype=\"webfilter\" "
                + "eventtype=\"urlfilter\" level=\"information\" srcip=172.21.1.220 hostname=\"www.youtube.com\" "
                + "action=\"passthrough\" msg=\"URL \\\"x\\\" liberada\" catdesc=\"Streaming Media and Download\"";
        Mensagem m = Interpretador.syslog(linha, "172.21.9.1", AGORA, FUSO, 16384);
        assertEquals(Fluxos.FIREWALL, m.fluxo());
        assertEquals("FGT-PMA-01", m.origem());
        assertEquals("fortigate/utm", m.aplicacao());
        assertEquals(6, m.nivel());
        assertEquals("www.youtube.com", m.campos().get("hostname"));
        assertEquals("172.21.1.220", m.campos().get("srcip"));
        assertEquals("Streaming Media and Download", m.campos().get("catdesc"));
        assertEquals("URL \"x\" liberada", m.campos().get("msg"));
        // eventtime em nanossegundos (FortiOS 7) vira o horário local
        assertEquals(LocalDateTime.of(2026, 10, 27, 11, 19, 3), m.momento().withNano(0));
    }

    @Test
    void syslog_rfc3164_com_tag() {
        Mensagem m = Interpretador.syslog("<34>Oct 27 11:05:01 srv-arquivos sshd[812]: Failed password for root",
                "10.0.0.5", AGORA, FUSO, 16384);
        assertEquals("srv-arquivos", m.origem());
        assertEquals("sshd", m.aplicacao());
        assertEquals(2, m.nivel());
        assertEquals("812", m.campos().get("pid"));
        assertEquals("Failed password for root", m.mensagem());
        assertEquals(LocalDateTime.of(2026, 10, 27, 11, 5, 1), m.momento());
        assertEquals(Fluxos.MAQUINAS, m.fluxo());
    }

    @Test
    void syslog_rfc5424_com_dados_estruturados() {
        Mensagem m = Interpretador.syslog("<165>1 2026-10-27T14:00:00.000Z web01 nginx 99 ID47 [x@1 usuario=\"ana\" "
                + "codigo=\"500\"] erro na requisição", "10.0.0.6", AGORA, FUSO, 16384);
        assertEquals("web01", m.origem());
        assertEquals("nginx", m.aplicacao());
        assertEquals("ana", m.campos().get("usuario"));
        assertEquals("500", m.campos().get("codigo"));
        assertEquals("erro na requisição", m.mensagem());
        assertEquals(LocalDateTime.of(2026, 10, 27, 11, 0), m.momento()); // 14h UTC = 11h em Brasília
    }

    @Test
    void gelf_evento_do_windows_conta_bloqueada() throws Exception {
        String json = "{\"version\":\"1.1\",\"host\":\"DC01\",\"short_message\":\"A user account was locked out.\","
                + "\"timestamp\":1793111000.5,\"level\":6,\"_EventID\":4740,\"_Channel\":\"Security\","
                + "\"_TargetUserName\":\"joao.silva\",\"_TargetDomainName\":\"PMA\",\"_SubjectUserName\":\"DC01$\"}";
        Mensagem m = Interpretador.gelf(json, "10.0.0.2", AGORA, FUSO, 16384);
        assertEquals(Fluxos.CONTAS, m.fluxo());
        assertEquals("DC01", m.origem());
        assertEquals("Security", m.aplicacao());
        assertEquals("4740", m.campos().get("event_id"));
        assertEquals("joao.silva", m.campos().get("usuario"));
        assertEquals("Conta bloqueada", m.campos().get("acao"));
    }

    @Test
    void gelf_winlogbeat_arquivo_apagado() throws Exception {
        String json = "{\"version\":\"1.1\",\"host\":\"FS01\",\"short_message\":\"An attempt was made to access an object.\","
                + "\"_winlogbeat_event_code\":\"4663\",\"_winlogbeat_winlog_event_data_SubjectUserName\":\"maria\","
                + "\"_winlogbeat_winlog_event_data_ObjectName\":\"D:\\\\Saude\\\\planilha.xlsx\","
                + "\"_winlogbeat_winlog_event_data_AccessMask\":\"0x10000\"}";
        Mensagem m = Interpretador.gelf(json, "10.0.0.3", AGORA, FUSO, 16384);
        assertEquals(Fluxos.ARQUIVOS, m.fluxo());
        assertEquals("maria", m.campos().get("usuario"));
        assertEquals("D:\\Saude\\planilha.xlsx", m.campos().get("objeto"));
        assertEquals("Arquivo apagado", m.campos().get("acao"));
        assertEquals(AGORA, m.momento()); // sem timestamp: horário de chegada
    }

    @Test
    void data_no_futuro_fica_com_a_chegada() {
        Mensagem m = Interpretador.syslog("<14>Dec 31 23:59:59 relogio-errado app: oi", "10.0.0.9",
                LocalDateTime.of(2026, 1, 1, 0, 0, 30), FUSO, 16384);
        // 31/12 de 2026 seria futuro: vira 31/12/2025
        assertEquals(LocalDateTime.of(2025, 12, 31, 23, 59, 59), m.momento());
    }

    @Test
    void texto_sem_cabecalho() {
        Mensagem m = Interpretador.syslog("qualquer coisa", "10.0.0.10", AGORA, FUSO, 16384);
        assertEquals("10.0.0.10", m.origem());
        assertEquals("qualquer coisa", m.mensagem());
        assertEquals(6, m.nivel());
    }

}
