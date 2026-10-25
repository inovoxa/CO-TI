package cn.iocoder.yudao.module.inventario.dominio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.*;

class InventarioDominioTest {

    private static byte[] amostra() throws Exception {
        try (InputStream in = InventarioDominioTest.class.getResourceAsStream("/inventario/ocs-windows.xml")) {
            return in.readAllBytes();
        }
    }

    private static byte[] zlib(byte[] dados) {
        return Protocolo.comprimir(dados);
    }

    @Test
    void leXmlDoAgenteOcsComprimidoComZlibOuGzip() throws Exception {
        Protocolo.Pedido p = Protocolo.ler(zlib(amostra()));
        assertEquals("INVENTORY", p.consulta());
        assertEquals("UBS-CENTRO-07-2026-10-20-08-15-02", p.deviceId());
        assertEquals(2, p.lista("MEMORIES").size());
        assertEquals("Dell Inc.", p.unico("BIOS").get("SMANUFACTURER"));
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(gz)) {
            g.write(amostra());
        }
        assertEquals("INVENTORY", Protocolo.ler(gz.toByteArray()).consulta());
        assertEquals("INVENTORY", Protocolo.ler(amostra()).consulta(), "sem compressão");
        Deflater cru = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        cru.setInput(amostra());
        cru.finish();
        byte[] buf = new byte[200_000];
        int n = cru.deflate(buf);
        assertEquals("INVENTORY", Protocolo.ler(java.util.Arrays.copyOf(buf, n)).consulta(), "deflate sem cabeçalho");
    }

    @Test
    void prologoEXxeBloqueado() {
        String prolog = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><REQUEST><DEVICEID>PC-1-2026-01-01-00-00-00</DEVICEID><QUERY>PROLOG</QUERY></REQUEST>";
        Protocolo.Pedido p = Protocolo.ler(zlib(prolog.getBytes(StandardCharsets.UTF_8)));
        assertEquals("PROLOG", p.consulta());
        assertEquals("PC-1-2026-01-01-00-00-00", p.deviceId());
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><REQUEST><QUERY>&x;</QUERY></REQUEST>";
        assertThrows(Protocolo.ProtocoloInvalido.class, () -> Protocolo.ler(xxe.getBytes(StandardCharsets.UTF_8)));
        assertThrows(Protocolo.ProtocoloInvalido.class, () -> Protocolo.ler(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void bombaDeCompressaoEhRecusada() {
        byte[] zeros = new byte[Protocolo.MAXIMO_DESCOMPRIMIDO + 1024];
        assertThrows(Protocolo.ProtocoloInvalido.class, () -> Protocolo.ler(zlib(zeros)));
    }

    @Test
    void respostaComprimidaComoOServidorOcs() throws Exception {
        byte[] r = Protocolo.replyOcs(Map.of("RESPONSE", "SEND", "PROLOG_FREQ", 12));
        String xml = new String(new InflaterInputStream(new java.io.ByteArrayInputStream(r)).readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<REPLY>") && xml.contains("<RESPONSE>SEND</RESPONSE>") && xml.contains("<PROLOG_FREQ>12</PROLOG_FREQ>"), xml);
    }

    @Test
    void normalizaODesktop() throws Exception {
        Normalizacao.Resultado r = Normalizacao.normalizar(Protocolo.ler(zlib(amostra())), LocalDate.of(2026, 10, 24));
        Normalizacao.Resumo a = r.resumo();
        assertEquals("desktop", a.tipo());
        assertEquals("UBS-CENTRO-07", a.nome());
        assertEquals("7GH2K93", a.numeroSerie());
        assertEquals("PMA-004521", a.patrimonio());
        assertEquals("Microsoft Windows 11 Pro", a.soNome());
        assertFalse(a.soSemSuporte());
        assertEquals(8, a.cpuNucleos());
        assertEquals(16135, a.memoriaMb());
        assertEquals(486400L, a.discoTotalMb(), "só o disco local, não a unidade de rede");
        assertEquals(153600L, a.discoLivreMb());
        assertEquals("A4:BB:6D:12:34:56", a.mac(), "a placa física, não a virtual do Hyper-V");
        assertEquals("maria.silva", a.usuarioLogin());
        assertEquals("ativo", a.antivirus(), "Windows 11 tem o Defender");
        assertEquals("ssd", a.discoTipo(), "Samsung SSD 980");
        assertEquals(3, r.softwares().size());
        assertEquals(java.time.LocalDate.of(2026, 9, 30), r.softwares().get(0).instaladoEm());
        assertEquals("64", r.softwares().get(0).arquitetura());
        assertEquals("32 bits", r.softwares().get(2).arquitetura());
        List<Normalizacao.Componente> mem = r.componentes().stream().filter(c -> c.tipo().equals("memoria")).toList();
        assertEquals(2, mem.size());
        assertEquals("M378A1K43EB2|1|8192", mem.get(0).chave());
        assertEquals("8192 MB · DDR4 · 3200 MHz", mem.get(0).descricao());
        assertTrue(r.componentes().stream().anyMatch(c -> c.tipo().equals("monitor") && c.chave().equals("CN0V1X7H|DELL P2422H|Monitor PnP genérico")));
    }

    @Test
    void regras() {
        assertEquals("notebook", Normalizacao.tipo("Notebook", "Windows 11", "Physical", "Dell", "Latitude"));
        assertEquals("servidor", Normalizacao.tipo("Desktop", "Microsoft Windows Server 2019 Standard", null, "Dell", "R740"));
        assertEquals("virtual", Normalizacao.tipo("Other", "Ubuntu", null, "VMware, Inc.", "VMware Virtual Platform"));
        assertTrue(Normalizacao.semSuporte("Microsoft Windows 7 Professional", null, LocalDate.of(2026, 1, 1)));
        assertTrue(Normalizacao.semSuporte("Microsoft Windows 10 Pro", null, LocalDate.of(2026, 1, 1)));
        assertFalse(Normalizacao.semSuporte("Microsoft Windows 10 Pro", null, LocalDate.of(2025, 1, 1)));
        assertFalse(Normalizacao.semSuporte("Microsoft Windows 10 Enterprise LTSC", null, LocalDate.of(2026, 1, 1)));
        assertFalse(Normalizacao.semSuporte("Microsoft Windows Server 2016", null, LocalDate.of(2026, 10, 1)));
        assertTrue(Normalizacao.generico("To Be Filled By O.E.M."));
        assertTrue(Normalizacao.generico("00000000-0000-0000-0000-000000000000"));
        assertFalse(Normalizacao.generico("7GH2K93"));
        assertEquals("maria.silva", Normalizacao.usuario("PREFEITURA\\maria.silva"));
        assertEquals("joao", Normalizacao.usuario("joao@prefeitura.local"));
        assertNull(Normalizacao.usuario("SYSTEM"));
        assertEquals(Normalizacao.chaveSoftware("Google  Chrome ", "Google LLC"), Normalizacao.chaveSoftware("google  chrome", "GOOGLE LLC"));
    }

    @Test
    void ipdiscoverNoProtocolo() throws Exception {
        String xml = "<REQUEST><DEVICEID>PC-1-2026-01-01-00-00-00</DEVICEID><QUERY>INVENTORY</QUERY><CONTENT>"
                + "<HARDWARE><NAME>PC-1</NAME></HARDWARE><IPDISCOVER><H><I>10.20.5.1</I><M>00:11:22:33:44:55</M><N>gw</N></H>"
                + "<H><I>10.20.5.9</I><M>aa:bb:cc:dd:ee:ff</M><N>-</N></H></IPDISCOVER></CONTENT></REQUEST>";
        Protocolo.Pedido p = Protocolo.ler(zlib(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, p.lista("IPDISCOVER").size());
        assertEquals("10.20.5.9", p.lista("IPDISCOVER").get(1).get("I"));
        assertEquals("gw", p.lista("IPDISCOVER").get(0).get("N"));
        assertEquals("PC-1", p.unico("HARDWARE").get("NAME"));

        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("RESPONSE", "SEND");
        r.put("OPTION", Map.of("NAME", "IPDISCOVER", "PARAM", new Protocolo.ComAtributos(
                new java.util.LinkedHashMap<>(Map.of("IPDISC_LAT", 100)), "10.20.0.0")));
        String reply = new String(new InflaterInputStream(new java.io.ByteArrayInputStream(Protocolo.replyOcs(r))).readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(reply.contains("<PARAM IPDISC_LAT=\"100\">10.20.0.0</PARAM>") && reply.contains("<NAME>IPDISCOVER</NAME>"), reply);
    }

    @Test
    void snmpImpressoraESwitch() {
        Map<String, String> v = new java.util.HashMap<>();
        v.put(SnmpLeitura.SYS_DESCR, "Brother NC-8300h, Firmware Ver.1.10");
        v.put(SnmpLeitura.SYS_OBJECT_ID, "1.3.6.1.4.1.2435.2.3.9.1");
        v.put(SnmpLeitura.SYS_NAME, "BRN3C2AF4");
        v.put(SnmpLeitura.PRT_SERIAL, "E78123K9N456");
        v.put(SnmpLeitura.PRT_PAGINAS, "48213");
        v.put(SnmpLeitura.HR_DEVICE_DESCR, "Brother HL-L6402DW series");
        Map<String, Map<String, String>> t = new java.util.HashMap<>();
        t.put(SnmpLeitura.PRT_SUPRIMENTO_DESCR, Map.of("1", "Toner Preto", "2", "Cilindro"));
        t.put(SnmpLeitura.PRT_SUPRIMENTO_MAX, Map.of("1", "100", "2", "-2"));
        t.put(SnmpLeitura.PRT_SUPRIMENTO_NIVEL, Map.of("1", "7", "2", "-3"));
        t.put(SnmpLeitura.IF_DESCR, Map.of("1", "Ethernet"));
        t.put(SnmpLeitura.IF_MAC, Map.of("1", "3c:2a:f4:aa:bb:cc"));
        SnmpLeitura.Resultado r = SnmpLeitura.interpretar("10.20.5.50", new SnmpLeitura.Dados(v, t));
        assertEquals("impressora", r.resumo().tipo());
        assertEquals("Brother", r.resumo().fabricante());
        assertEquals("Brother HL-L6402DW series", r.resumo().modelo());
        assertEquals("E78123K9N456", r.resumo().numeroSerie());
        assertEquals("3C:2A:F4:AA:BB:CC", r.resumo().mac());
        assertEquals(48213L, r.paginas());
        SnmpLeitura.Suprimento toner = r.suprimentos().stream().filter(s -> s.descricao().equals("Toner Preto")).findFirst().orElseThrow();
        assertEquals(7, toner.percentual());
        assertNull(r.suprimentos().stream().filter(s -> s.descricao().equals("Cilindro")).findFirst().orElseThrow().percentual(),
                "nível -3 (tem algum) e máximo -2 (desconhecido) não viram %");

        Map<String, String> sw = new java.util.HashMap<>();
        sw.put(SnmpLeitura.SYS_DESCR, "Cisco IOS Software, C2960 Software");
        sw.put(SnmpLeitura.SYS_OBJECT_ID, ".1.3.6.1.4.1.9.1.1208");
        sw.put(SnmpLeitura.SYS_NAME, "sw-core-01");
        sw.put(SnmpLeitura.IF_NUMBER, "28");
        sw.put(SnmpLeitura.ENT_SERIAL, "FOC1234X0YZ");
        sw.put(SnmpLeitura.ENT_MODELO, "WS-C2960X-24TS-L");
        SnmpLeitura.Resultado s2 = SnmpLeitura.interpretar("10.20.0.2", new SnmpLeitura.Dados(sw, Map.of()));
        assertEquals("rede", s2.resumo().tipo());
        assertEquals("Cisco", s2.resumo().fabricante());
        assertEquals("WS-C2960X-24TS-L", s2.resumo().modelo());
        assertEquals("sw-core-01", s2.resumo().nome());
        assertNull(s2.paginas());
    }

    @Test
    void redesPermitidas() {
        List<String> privadas = List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fd00::/8");
        assertTrue(Redes.permitido("10.20.30.40", privadas));
        assertTrue(Redes.permitido("172.31.255.1", privadas));
        assertFalse(Redes.permitido("172.32.0.1", privadas));
        assertTrue(Redes.permitido("::ffff:192.168.1.5", privadas), "IPv4 mapeado em IPv6");
        assertTrue(Redes.permitido("fd12::1", privadas));
        assertFalse(Redes.permitido("200.1.2.3", privadas));
        assertFalse(Redes.permitido("exemplo.com", privadas), "nome não é resolvido");
        assertFalse(Redes.permitido(null, privadas));
        assertTrue(Redes.permitido("200.1.2.3", List.of()), "lista vazia = qualquer origem");
        assertTrue(Redes.permitido("200.1.2.3", List.of("")), "variável de ambiente vazia = qualquer origem");
        assertTrue(Redes.permitido("200.1.2.3", List.of("0.0.0.0/0")));
        assertTrue(Redes.permitido("200.1.2.3", List.of("200.1.2.3")));
    }

    @Test
    void jsonDoGlpiAgent() {
        String j = """
                {"action":"inventory","deviceid":"nb-ti-01-2026-10-01-10-00-00","itemtype":"Computer","content":{
                 "hardware":{"name":"NB-TI-01","memory":15872,"uuid":"1234-ABCD"},
                 "operatingsystem":{"full_name":"Microsoft Windows 10 Pro","version":"22H2","arch":"64-bit"},
                 "bios":{"ssn":"5CG1234XYZ","smanufacturer":"HP","smodel":"EliteBook 840 G8","type":"Notebook"},
                 "cpus":[{"name":"Intel(R) Core(TM) i5-1145G7","core":4,"thread":8}],
                 "antivirus":[{"name":"Windows Defender","enabled":true,"uptodate":false}],
                 "users":[{"login":"joao.ti","domain":"PREFEITURA"}],
                 "softwares":[{"name":"Mozilla Firefox","version":"131.0","publisher":"Mozilla","install_date":"2026-10-01"}]}}""";
        Protocolo.Pedido p = Protocolo.ler(zlib(j.getBytes(StandardCharsets.UTF_8)));
        assertEquals("glpi", p.formato());
        assertEquals("INVENTORY", p.consulta());
        Normalizacao.Resultado r = Normalizacao.normalizar(p, LocalDate.of(2026, 10, 24));
        assertEquals("notebook", r.resumo().tipo());
        assertEquals("Microsoft Windows 10 Pro", r.resumo().soNome());
        assertTrue(r.resumo().soSemSuporte());
        assertEquals(4, r.resumo().cpuNucleos());
        assertEquals("desatualizado", r.resumo().antivirus());
        assertEquals("joao.ti", r.resumo().usuarioLogin());
        assertEquals(LocalDate.of(2026, 10, 1), r.softwares().get(0).instaladoEm());
    }

    private static Map<String, String> disco(String modelo, String tipo) {
        return Map.of("MODEL", modelo, "TYPE", tipo, "NAME", "PHYSICALDRIVE0");
    }

    @Test
    void classificaOTipoDeDisco() {
        String fixo = "Fixed hard disk media";
        // SSD pelo nome, pela interface ou pelo modelo OEM
        for (String m : List.of("Samsung SSD 870 EVO 500GB", "KINGSTON SA400S37240G", "SAMSUNG MZVLB256HBHQ-000L7", "WDC WDS240G2G0A-00JH30",
                "WDC PC SN530 SDBPNPZ-256G", "KINGSTON SNVS250G", "CT480BX500SSD1", "SK hynix HFS256G39TND-N210A", "Micron MTFDDAK256TBN")) {
            assertEquals("ssd", Normalizacao.classeDisco(disco(m, fixo)), m);
        }
        assertEquals("ssd", Normalizacao.classeDisco(Map.of("MODEL", "Generic 512G", "INTERFACE", "NVMe")));
        // HD pelo modelo
        for (String m : List.of("WDC WD10EZEX-08WN4A0", "ST1000DM010-2EP102", "TOSHIBA MQ01ABD100", "Hitachi HTS545050A7E380", "SAMSUNG HD502HJ",
                "TOSHIBA DT01ACA100")) {
            assertEquals("hdd", Normalizacao.classeDisco(disco(m, fixo)), m);
        }
        assertEquals("desconhecido", Normalizacao.classeDisco(disco("VMware Virtual disk SCSI Disk Device", fixo)));
        assertNull(Normalizacao.classeDisco(disco("SanDisk Cruzer Blade USB Device", "Removable Media")), "pen drive não conta");
        // a máquina
        assertEquals("ssd", Normalizacao.discoTipo(List.of(disco("Samsung SSD 860", fixo), disco("SanDisk Cruzer USB Device", "Removable Media"))));
        assertEquals("misto", Normalizacao.discoTipo(List.of(disco("KINGSTON SA400S37240G", fixo), disco("ST1000DM010-2EP102", fixo))));
        assertEquals("hdd", Normalizacao.discoTipo(List.of(disco("WDC WD10EZEX-08WN4A0", fixo), disco("Disco genérico", fixo))));
        assertEquals("desconhecido", Normalizacao.discoTipo(List.of(disco("Disco genérico", fixo))));
        assertNull(Normalizacao.discoTipo(List.of()));
    }
}
