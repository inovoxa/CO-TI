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
     * Aceita ldap:// sem StartTLS: as senhas passam abertas pela rede até o DC. Só para quando o DC não tem certificado e a
     * ligação fica toda na rede interna (como o GLPI sem SSL).
     */
    private boolean semCriptografia = false;
    /**
     * Confia em qualquer certificado do DC. Só para laboratório: em produção importe a CA do AD
     * no truststore da JVM ou aponte {@link #truststore}.
     */
    private boolean confiarEmQualquerCertificado = false;
    /** Truststore (PKCS12/JKS) com a CA do AD; vazio = truststore padrão da JVM. */
    private String truststore;
    private String truststoreSenha;
    /** Certificado(s) da CA do AD em PEM, colados na tela. Tem prioridade sobre {@link #truststore}. */
    private String certificadoCa;

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
     * Cria departamentos espelhando as OUs e põe cada usuário no da sua OU. Desligado, quem entra pela primeira vez vai para
     * {@link #deptRaizId} e o departamento dado à mão no portal não muda mais.
     */
    private boolean espelharOus = true;

    /**
     * Grupo do AD → código do perfil no portal (system_role.code). A chave pode ser o DN completo ou só o CN.
     * Perfis fora deste mapa nunca são tocados pela sincronização.
     */
    private Map<String, String> gruposPerfis = new LinkedHashMap<>();

    /**
     * Grupo(s) do AD cujos membros aprovam pedidos (CN ou DN, vários separados por ";"). Dá o perfil
     * {@value #PERFIL_APROVADOR_AD}, controlado pelo AD; o perfil "aprovador", dado à mão no portal, continua valendo.
     */
    private String grupoAprovadores;

    public static final String PERFIL_APROVADOR_AD = "aprovador_ad";

    /** {@link #gruposPerfis} mais os grupos de aprovadores. */
    public Map<String, String> getGruposPerfisEfetivos() {
        Map<String, String> m = new LinkedHashMap<>(gruposPerfis);
        if (grupoAprovadores != null) {
            for (String g : grupoAprovadores.split(";")) {
                if (!g.isBlank()) {
                    m.put(g.trim(), PERFIL_APROVADOR_AD);
                }
            }
        }
        return m;
    }

    /** Perfil dado a quem entra pela primeira vez (código em system_role); vazio = nenhum. "common" = portal. */
    private String perfilPadrao = "common";

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

    /** Cópia independente: a configuração da tela é aplicada sobre ela sem mexer na que veio das variáveis de ambiente. */
    public AdProperties copia() {
        AdProperties c = new AdProperties();
        c.habilitado = habilitado;
        c.url = url;
        c.startTls = startTls;
        c.semCriptografia = semCriptografia;
        c.confiarEmQualquerCertificado = confiarEmQualquerCertificado;
        c.truststore = truststore;
        c.truststoreSenha = truststoreSenha;
        c.certificadoCa = certificadoCa;
        c.bindDn = bindDn;
        c.bindSenha = bindSenha;
        c.baseUsuarios = baseUsuarios;
        c.filtroUsuarios = filtroUsuarios;
        c.ouRaiz = ouRaiz;
        c.deptRaizId = deptRaizId;
        c.espelharOus = espelharOus;
        c.gruposPerfis = new LinkedHashMap<>(gruposPerfis);
        c.grupoAprovadores = grupoAprovadores;
        c.perfilPadrao = perfilPadrao;
        c.cronSincronizacao = cronSincronizacao;
        c.desativarAusentes = desativarAusentes;
        c.timeoutConexao = timeoutConexao;
        c.timeoutOperacao = timeoutOperacao;
        c.tamanhoPool = tamanhoPool;
        c.tamanhoPagina = tamanhoPagina;
        return c;
    }

}
