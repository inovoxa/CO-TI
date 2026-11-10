package cn.iocoder.yudao.module.monitoramento.dominio;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.monitoramento.dominio.TiposMonitor.*;

/** Validação dos tipos de serviço, sem dependência de servidor. Erro vem como {@link IllegalArgumentException}. */
public final class RegrasServicos {

    private static final Pattern OID = Pattern.compile("^\\.?[0-9]+(\\.[0-9]+)+$");
    /** Nome ou id de container, como o Docker aceita */
    private static final Pattern CONTAINER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.\\-]{0,127}$");
    private static final Pattern BANCO = Pattern.compile("^[A-Za-z0-9_$.\\-]{1,128}$");
    private static final Set<String> OPERADORES = Set.of("==", "!=", "<", "<=", ">", ">=", "contem");

    /** O que o formulário (ou a importação) mandou, já sem espaços nas pontas. */
    public record Pedido(String tipo, String alvo, Integer porta, String usuario, String banco, String consulta, String operador,
                         String snmpVersao, String snmpNivel, String snmpAuth, String snmpPriv, String smtpSeguranca, Long dockerHostId,
                         boolean temSenha, boolean temSegredo) {
    }

    private RegrasServicos() {
    }

    public static void validar(Pedido p) {
        String t = p.tipo();
        if (usaPorta(t) && p.porta() != null && (p.porta() < 1 || p.porta() > 65535)) {
            throw new IllegalArgumentException("porta deve ser de 1 a 65535");
        }
        if (p.operador() != null && !OPERADORES.contains(p.operador())) {
            throw new IllegalArgumentException("condição deve ser ==, !=, <, <=, >, >= ou contem");
        }
        if (SQL.contains(t)) {
            exigirHost(p.alvo());
            if (p.usuario() == null) {
                throw new IllegalArgumentException("informe o usuário do banco (de preferência um só de leitura)");
            }
            if (p.banco() != null && !BANCO.matcher(p.banco()).matches()) {
                throw new IllegalArgumentException("nome do banco inválido");
            }
            if ("oracle".equals(t) && p.banco() == null) {
                throw new IllegalArgumentException("informe o serviço do Oracle (service name)");
            }
            return;
        }
        switch (t) {
            case "snmp" -> {
                exigirHost(p.alvo());
                if (p.consulta() == null || !OID.matcher(p.consulta()).matches()) {
                    throw new IllegalArgumentException("informe o OID, ex.: 1.3.6.1.2.1.1.3.0");
                }
                String v = p.snmpVersao() == null ? "v2c" : p.snmpVersao();
                if (!SNMP_VERSOES.contains(v)) {
                    throw new IllegalArgumentException("versão do SNMP deve ser v1, v2c ou v3");
                }
                if ("v3".equals(v)) {
                    if (p.usuario() == null) {
                        throw new IllegalArgumentException("informe o usuário do SNMP v3");
                    }
                    String nivel = p.snmpNivel() == null ? "authPriv" : p.snmpNivel();
                    if (!SNMP_NIVEIS.contains(nivel) || (p.snmpAuth() != null && !SNMP_AUTHS.contains(p.snmpAuth()))
                            || (p.snmpPriv() != null && !SNMP_PRIVS.contains(p.snmpPriv()))) {
                        throw new IllegalArgumentException("nível ou protocolo do SNMP v3 inválido");
                    }
                    if (!"noAuthNoPriv".equals(nivel) && !p.temSenha()) {
                        throw new IllegalArgumentException("informe a senha de autenticação do SNMP v3");
                    }
                    if ("authPriv".equals(nivel) && !p.temSegredo()) {
                        throw new IllegalArgumentException("informe a senha de privacidade do SNMP v3");
                    }
                } else if (!p.temSenha()) {
                    throw new IllegalArgumentException("informe a comunidade do SNMP");
                }
            }
            case "docker" -> {
                if (p.dockerHostId() == null) {
                    throw new IllegalArgumentException("escolha o servidor Docker");
                }
                if (p.consulta() == null || !CONTAINER.matcher(p.consulta()).matches()) {
                    throw new IllegalArgumentException("informe o nome ou o id do container");
                }
            }
            case "redis", "mongodb", "mqtt" -> exigirHost(p.alvo());
            case "smtp" -> {
                exigirHost(p.alvo());
                if (p.smtpSeguranca() != null && !SMTP_SEGURANCAS.contains(p.smtpSeguranca())) {
                    throw new IllegalArgumentException("segurança do SMTP deve ser nenhuma, starttls ou tls");
                }
            }
            case "radius" -> {
                exigirHost(p.alvo());
                if (p.usuario() == null || !p.temSenha() || !p.temSegredo()) {
                    throw new IllegalArgumentException("informe o usuário, a senha e o segredo compartilhado do RADIUS");
                }
            }
            case "rabbitmq" -> {
                if (p.alvo() == null || !p.alvo().matches("(?i)https?://[^\\s/$.?#][^\\s]*")) {
                    throw new IllegalArgumentException("informe a URL da API de gerenciamento, ex.: http://servidor:15672");
                }
                if (p.usuario() == null || !p.temSenha()) {
                    throw new IllegalArgumentException("informe o usuário e a senha do RabbitMQ");
                }
            }
            default -> {
            }
        }
    }

    private static void exigirHost(String alvo) {
        if (!Regras.hostValido(alvo)) {
            throw new IllegalArgumentException("informe o host (nome ou IP)");
        }
    }

    /** URL do JDBC; host e banco já validados (sem espaço nem caractere que mude a URL). */
    public static String urlJdbc(String tipo, String host, int porta, String banco, boolean tls, boolean ignorarTls, int limiteS) {
        String h = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return switch (tipo) {
            case "postgres" -> "jdbc:postgresql://" + h + ":" + porta + "/" + (banco == null ? "postgres" : banco)
                    + "?connectTimeout=" + limiteS + "&socketTimeout=" + limiteS + "&loginTimeout=" + limiteS
                    // o driver do PostgreSQL só usa os certificados confiáveis do Java com o DefaultJavaSSLFactory
                    + (tls ? (ignorarTls ? "&sslmode=require&sslfactory=org.postgresql.ssl.NonValidatingFactory"
                    : "&sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory") : "&sslmode=prefer")
                    + "&ApplicationName=CO-TI-Monitor";
            case "mysql" -> "jdbc:mysql://" + h + ":" + porta + "/" + (banco == null ? "" : banco)
                    + "?connectTimeout=" + limiteS * 1000 + "&socketTimeout=" + limiteS * 1000
                    + "&sslMode=" + (tls ? (ignorarTls ? "REQUIRED" : "VERIFY_IDENTITY") : "PREFERRED") + "&allowPublicKeyRetrieval=true";
            case "sqlserver" -> "jdbc:sqlserver://" + h + ":" + porta + ";" + (banco == null ? "" : "databaseName=" + banco + ";")
                    + "encrypt=" + tls + ";trustServerCertificate=" + ignorarTls + ";loginTimeout=" + limiteS
                    + ";socketTimeout=" + limiteS * 1000 + ";applicationName=CO-TI-Monitor";
            case "oracle" -> "jdbc:oracle:thin:@" + (tls ? "tcps://" : "//") + h + ":" + porta + "/" + banco;
            default -> throw new IllegalArgumentException("tipo sem JDBC: " + tipo);
        };
    }

    public static String minusculo(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

}
