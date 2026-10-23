package cn.iocoder.yudao.module.identidade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuração do Active Directory ({@code coti.ad.*}). Senhas sempre por variável de ambiente.
 */
@ConfigurationProperties(prefix = "coti.ad")
@Data
public class AdProperties {

    /** Liga o login e a sincronização pelo AD. Desligado, o portal usa só contas locais. */
    private boolean habilitado = false;

    /** Ex.: ldaps://dc01.empresa.local:636 (vários separados por vírgula para failover). */
    private String url;
    /** Usa StartTLS numa URL ldap:// (porta 389). Ignorado com ldaps://. */
    private boolean startTls = false;
    /**
     * Confia em qualquer certificado do DC. Só para laboratório: em produção importe a CA do AD
     * no truststore da JVM ou aponte {@link #truststore}.
     */
    private boolean confiarEmQualquerCertificado = false;
    /** Truststore (PKCS12/JKS) com a CA do AD; vazio = truststore padrão da JVM. */
    private String truststore;
    private String truststoreSenha;

    /** Conta de serviço só de leitura usada nas buscas (DN ou UPN). */
    private String bindDn;
    private String bindSenha;

    /** Onde procurar usuários, ex.: OU=Empresa,DC=empresa,DC=local. */
    private String baseUsuarios;
    /** Filtro extra para quem pode entrar no portal, ex.: (memberOf=CN=GG-Portal,OU=Grupos,DC=...) */
    private String filtroUsuarios = "(&(objectCategory=person)(objectClass=user))";

    /**
     * OU que vira a raiz da árvore de departamentos. As OUs abaixo dela viram departamentos filhos.
     * Vazio = usa {@link #baseUsuarios}.
     */
    private String ouRaiz;
    /** Departamento (system_dept.id) que recebe a árvore espelhada do AD. */
    private Long deptRaizId = 100L;

    /**
     * Grupo do AD → código do perfil no portal (system_role.code). A chave pode ser o DN completo ou só o CN.
     * Perfis fora deste mapa nunca são tocados pela sincronização.
     */
    private Map<String, String> gruposPerfis = new LinkedHashMap<>();

    /** Sincronização completa periódica (cron do Spring). Vazio desliga. */
    private String cronSincronizacao = "0 */30 * * * *";
    /** Desativa no portal quem sumiu do AD ou foi desativado lá. */
    private boolean desativarAusentes = true;

    private Duration timeoutConexao = Duration.ofSeconds(5);
    private Duration timeoutOperacao = Duration.ofSeconds(15);
    private int tamanhoPool = 4;
    private int tamanhoPagina = 500;

    public String getOuRaizEfetiva() {
        return ouRaiz == null || ouRaiz.isBlank() ? baseUsuarios : ouRaiz;
    }

}
