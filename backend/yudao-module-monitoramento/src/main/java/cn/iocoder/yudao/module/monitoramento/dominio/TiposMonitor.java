package cn.iocoder.yudao.module.monitoramento.dominio;

import java.util.Map;
import java.util.Set;

/** Os tipos de monitor e o que cada um usa. */
public final class TiposMonitor {

    public static final Set<String> HTTP = Set.of("http", "palavra", "json");
    public static final Set<String> SQL = Set.of("postgres", "mysql", "sqlserver", "oracle");
    /** Tipos que vieram depois do Kuma "básico": usam usuário, senha ou opções que a planilha não traz */
    public static final Set<String> SERVICOS = Set.of("postgres", "mysql", "sqlserver", "oracle", "snmp", "docker", "redis", "mongodb",
            "smtp", "radius", "mqtt", "rabbitmq");
    public static final Set<String> TODOS = Set.of("http", "palavra", "json", "tcp", "ping", "dns", "push", "manual", "postgres", "mysql",
            "sqlserver", "oracle", "snmp", "docker", "redis", "mongodb", "smtp", "radius", "mqtt", "rabbitmq");

    /** Porta usada quando não é informada; o tipo que está aqui guarda a porta */
    public static final Map<String, Integer> PORTA_PADRAO = Map.ofEntries(Map.entry("tcp", 0), Map.entry("postgres", 5432),
            Map.entry("mysql", 3306), Map.entry("sqlserver", 1433), Map.entry("oracle", 1521), Map.entry("snmp", 161),
            Map.entry("redis", 6379), Map.entry("mongodb", 27017), Map.entry("smtp", 25), Map.entry("radius", 1812), Map.entry("mqtt", 1883));

    /** Consulta usada quando não é informada */
    public static final Map<String, String> CONSULTA_PADRAO = Map.of("postgres", "SELECT 1", "mysql", "SELECT 1", "sqlserver", "SELECT 1",
            "oracle", "SELECT 1 FROM DUAL");

    // opções (chaves do JSON em "opcoes")
    public static final String TLS = "tls";
    public static final String SNMP_VERSAO = "snmpVersao";
    public static final String SNMP_NIVEL = "snmpNivel";
    public static final String SNMP_AUTH = "snmpAuth";
    public static final String SNMP_PRIV = "snmpPriv";
    public static final String SMTP_SEGURANCA = "smtpSeguranca";

    public static final Set<String> SNMP_VERSOES = Set.of("v1", "v2c", "v3");
    public static final Set<String> SNMP_NIVEIS = Set.of("noAuthNoPriv", "authNoPriv", "authPriv");
    public static final Set<String> SNMP_AUTHS = Set.of("MD5", "SHA", "SHA256", "SHA512");
    public static final Set<String> SNMP_PRIVS = Set.of("DES", "AES", "AES256");
    /** nenhuma: texto puro; starttls: sobe para TLS depois do EHLO; tls: TLS desde a conexão (porta 465) */
    public static final Set<String> SMTP_SEGURANCAS = Set.of("nenhuma", "starttls", "tls");

    private TiposMonitor() {
    }

    public static boolean usaPorta(String tipo) {
        return PORTA_PADRAO.containsKey(tipo);
    }

}
