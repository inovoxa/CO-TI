package cn.iocoder.yudao.module.monitoramento.service.verificador;

import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dominio.RegrasServicos;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Protocolos falados direto (sem biblioteca) e regras dos tipos de serviço. */
class ServicosTest {

    private static final HexFormat HEX = HexFormat.of();

    // ------------------------------------------------------------------ RADIUS

    @Test
    void senhaRadiusComoNoExemploDaRfc2865() {
        // RFC 2865, 7.1: usuário nemo, senha arctangent, segredo xyzzy5461
        byte[] autenticador = HEX.parseHex("0f403f9473978057bd83d5cb98f4227a");
        byte[] escondida = Protocolos.esconderSenha("arctangent".getBytes(StandardCharsets.UTF_8), "xyzzy5461".getBytes(StandardCharsets.UTF_8), autenticador);
        assertEquals("0dbe708d93d413ce3196e43f782a0aee", HEX.formatHex(escondida));
    }

    @Test
    void pedidoRadiusTemAtributosEMessageAuthenticator() {
        byte[] autenticador = new byte[16];
        byte[] p = Protocolos.radiusPedido(7, autenticador, "teste", "senha", "segredo");
        assertEquals(Protocolos.ACCESS_REQUEST, p[0]);
        assertEquals(7, p[1]);
        assertEquals(p.length, ((p[2] & 0xff) << 8) | (p[3] & 0xff));
        // o último atributo é o Message-Authenticator (80), preenchido
        assertEquals(80, p[p.length - 18] & 0xff);
        assertFalse(Arrays.equals(new byte[16], Arrays.copyOfRange(p, p.length - 16, p.length)));
    }

    @Test
    void respostaRadiusSoValeComOMesmoSegredo() {
        byte[] pedidoAut = HEX.parseHex("00112233445566778899aabbccddeeff");
        byte[] resp = new byte[20];
        resp[0] = Protocolos.ACCESS_ACCEPT;
        resp[1] = 9;
        resp[3] = 20;
        byte[] base = Arrays.copyOf(resp, 20);
        System.arraycopy(pedidoAut, 0, base, 4, 16);
        byte[] comSegredo = Arrays.copyOf(base, 20 + 7);
        System.arraycopy("segredo".getBytes(StandardCharsets.UTF_8), 0, comSegredo, 20, 7);
        System.arraycopy(Protocolos.md5(comSegredo), 0, resp, 4, 16);
        assertTrue(Protocolos.respostaAutentica(resp, 20, pedidoAut, "segredo"));
        assertFalse(Protocolos.respostaAutentica(resp, 20, pedidoAut, "outro"));
    }

    // ------------------------------------------------------------------ MQTT e Redis

    @Test
    void connectMqttEComprimentoVariavel() {
        byte[] c = Protocolos.mqttConnect("a", null, null, 30);
        assertEquals("100d00044d5154540402001e000161", HEX.formatHex(c));
        byte[] grande = Protocolos.pacote(0x30, new byte[321]);
        assertEquals(0xc1, grande[1] & 0xff);
        assertEquals(0x02, grande[2] & 0xff);
    }

    @Test
    void leConteudoDoPublish() throws Exception {
        byte[] topico = "t/1".getBytes(StandardCharsets.UTF_8);
        byte[] corpo = new byte[2 + topico.length + 6];
        corpo[1] = (byte) topico.length;
        System.arraycopy(topico, 0, corpo, 2, topico.length);
        System.arraycopy("online".getBytes(StandardCharsets.UTF_8), 0, corpo, 2 + topico.length, 6);
        byte[] lido = Protocolos.lerPacoteMqtt(new ByteArrayInputStream(Protocolos.pacote(0x30, corpo)));
        assertEquals("online", Protocolos.conteudoPublish(lido));
    }

    @Test
    void comandoRedisEmResp() throws Exception {
        assertEquals("*1\r\n$4\r\nPING\r\n", new String(Protocolos.comandoRedis("PING"), StandardCharsets.UTF_8));
        assertEquals("*2\r\n$4\r\nAUTH\r\n$3\r\nsã\r\n".length() + 1, Protocolos.comandoRedis("AUTH", "sã").length);
        assertEquals("+PONG", Protocolos.linha(new ByteArrayInputStream("+PONG\r\n".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void respostaSmtpDeVariasLinhas() throws Exception {
        String r = VerificadoresServicos.respostaSmtp(new ByteArrayInputStream("250-mail.x\r\n250-STARTTLS\r\n250 OK\r\n221 tchau\r\n".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("250-mail.x\n250-STARTTLS\n250 OK", r);
    }

    // ------------------------------------------------------------------ Docker (PEM)

    @Test
    void chaveRsaPkcs1ViraPkcs8() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        byte[] pkcs8 = g.generateKeyPair().getPrivate().getEncoded();
        // PrivateKeyInfo de 2048 bits: cabeçalho de 26 bytes até o PKCS#1
        byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);
        assertArrayEquals(pkcs8, ClienteDocker.pkcs1ParaPkcs8(pkcs1));
        String pem = "-----BEGIN RSA PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pkcs1) + "\n-----END RSA PRIVATE KEY-----\n";
        PrivateKey k = ClienteDocker.chave(pem);
        assertEquals("RSA", k.getAlgorithm());
        String pem8 = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pkcs8) + "\n-----END PRIVATE KEY-----\n";
        assertEquals("RSA", ClienteDocker.chave(pem8).getAlgorithm());
    }

    @Test
    void chaveProtegidaOuInvalidaExplicaOQueFazer() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ClienteDocker.chave("lixo")).getMessage().contains("key.pem"));
        String cifrada = "-----BEGIN ENCRYPTED PRIVATE KEY-----\nAAAA\n-----END ENCRYPTED PRIVATE KEY-----";
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ClienteDocker.chave(cifrada)).getMessage().contains("senha"));
    }

    // ------------------------------------------------------------------ regras e comparação

    private static RegrasServicos.Pedido pedido(String tipo, String alvo, String usuario, String consulta, boolean senha, boolean segredo) {
        return new RegrasServicos.Pedido(tipo, alvo, null, usuario, null, consulta, null, null, null, null, null, null, null, senha, segredo);
    }

    @Test
    void regrasDosTipos() {
        assertDoesNotThrow(() -> RegrasServicos.validar(pedido("postgres", "db.local", "leitor", "SELECT 1", true, false)));
        assertThrows(IllegalArgumentException.class, () -> RegrasServicos.validar(pedido("postgres", "db.local", null, "SELECT 1", true, false)));
        assertThrows(IllegalArgumentException.class, () -> RegrasServicos.validar(pedido("oracle", "db.local", "u", "SELECT 1 FROM DUAL", true, false)));
        assertDoesNotThrow(() -> RegrasServicos.validar(pedido("snmp", "10.0.0.1", null, "1.3.6.1.2.1.1.3.0", true, false)));
        assertThrows(IllegalArgumentException.class, () -> RegrasServicos.validar(pedido("snmp", "10.0.0.1", null, "sysUpTime", true, false)));
        assertThrows(IllegalArgumentException.class, () -> RegrasServicos.validar(pedido("snmp", "10.0.0.1", null, "1.3.6.1", false, false)),
                "v2c sem comunidade");
        assertThrows(IllegalArgumentException.class, () -> RegrasServicos.validar(pedido("radius", "10.0.0.2", "u", null, true, false)),
                "RADIUS sem segredo");
        assertThrows(IllegalArgumentException.class, () -> RegrasServicos.validar(pedido("docker", null, null, "../etc", false, false)));
        assertThrows(IllegalArgumentException.class, () -> RegrasServicos.validar(pedido("rabbitmq", "servidor:15672", "u", null, true, false)));
        assertThrows(IllegalArgumentException.class, () -> RegrasServicos.validar(pedido("redis", "-oProxyCommand=x", null, null, false, false)));
    }

    @Test
    void urlJdbcDeCadaBanco() {
        assertTrue(RegrasServicos.urlJdbc("postgres", "db", 5432, null, false, false, 10).startsWith("jdbc:postgresql://db:5432/postgres?"));
        assertTrue(RegrasServicos.urlJdbc("postgres", "db", 5432, "app", true, false, 10).contains("sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"));
        assertTrue(RegrasServicos.urlJdbc("mysql", "db", 3306, "app", false, false, 10).contains("connectTimeout=10000"));
        assertTrue(RegrasServicos.urlJdbc("sqlserver", "db", 1433, "app", true, true, 10).contains("encrypt=true;trustServerCertificate=true"));
        assertEquals("jdbc:oracle:thin:@//db:1521/ORCL", RegrasServicos.urlJdbc("oracle", "db", 1521, "ORCL", false, false, 10));
        assertTrue(RegrasServicos.urlJdbc("postgres", "fe80::1", 5432, null, false, false, 5).contains("//[fe80::1]:5432/"));
    }

    @Test
    void comparaValorLido() {
        MonitorDO m = MonitorDO.builder().jsonOperador(">=").jsonEsperado("100").build();
        assertTrue(VerificadoresServicos.comparar(5, "OID", "150", m).ok());
        assertFalse(VerificadoresServicos.comparar(5, "OID", "99", m).ok());
        MonitorDO contem = MonitorDO.builder().jsonOperador("contem").jsonEsperado("Online").build();
        assertTrue(VerificadoresServicos.comparar(5, "OID", "UPS Online", contem).ok());
        assertTrue(VerificadoresServicos.comparar(5, "consulta", "1", MonitorDO.builder().build()).ok(), "sem valor esperado basta responder");
    }

}
