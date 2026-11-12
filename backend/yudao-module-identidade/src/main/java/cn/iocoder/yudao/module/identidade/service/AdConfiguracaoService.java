package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.identidade.ad.AdDiretorio;
import cn.iocoder.yudao.module.identidade.ad.AdIndisponivelException;
import cn.iocoder.yudao.module.identidade.ad.AdUsuario;
import cn.iocoder.yudao.module.identidade.config.AdProperties;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

/**
 * Configuração do AD feita na tela da Infraestrutura (como o "Diretório LDAP" do GLPI).
 *
 * <p>Sem nada salvo na tela, vale o que veio das variáveis {@code CO_TI_AD_*}. Depois do primeiro "Salvar", vale a tela. A
 * senha da conta de serviço fica cifrada com a chave-mestra e nunca volta para o navegador. Cada backend confere a cada 30 s
 * se a configuração mudou e refaz a conexão, sem reiniciar.
 */
@Slf4j
@Service
public class AdConfiguracaoService {

    static final String FILTRO_PADRAO = "(&(objectCategory=person)(objectClass=user))";
    private static final String CONTEXTO_SENHA = "ad:bind";
    private static final int LIMITE_CONTAGEM = 100_000;

    @Resource
    private AdProperties ambiente;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private CifradorSegredos cifrador;

    /** O que a tela mostra e salva. A senha não vem aqui: ela vai à parte e só é trocada quando preenchida. */
    @Data
    public static class Dados {
        private boolean habilitado;
        /** Um ou mais servidores (nome ou IP) separados por vírgula: o segundo é usado se o primeiro cair. */
        private String servidores;
        private Integer porta;
        /** ldaps | starttls | nenhuma (ldap:// sem cifra, só na rede interna) */
        private String seguranca;
        private String certificadoCa;
        private String bindDn;
        private String baseUsuarios;
        /** Condições a mais, somadas a "é uma pessoa": ex. (memberOf=CN=GG-Portal,OU=Grupos,DC=...) */
        private String filtro;
        private String ouRaiz;
        private boolean espelharOus;
        private Long deptRaizId;
        private String perfilPadrao;
        private String grupoAprovadores;
        private Map<String, String> gruposPerfis = new LinkedHashMap<>();
        private boolean desativarAusentes;
    }

    /** {@link Dados} mais o que só se lê. */
    public record Tela(Dados dados, boolean senhaSalva, String origem, String atualizadoPor, Date atualizadoEm,
                       boolean conectado, String erroConexao) {
    }

    public record Teste(boolean ok, List<String> etapas, String erro) {
    }

    public record CertificadoInfo(String assunto, String emissor, Date validoAte, String sha256, boolean ca, String pem) {
    }

    private record Estado(AdProperties props, Timestamp versao) {
    }

    private volatile Estado estado;
    private AdDiretorio diretorio;
    private String ultimoErro;

    // ------------------------------------------------------------------ leitura

    /** A configuração em vigor (tela por cima das variáveis de ambiente). */
    public AdProperties atual() {
        Estado e = estado;
        if (e == null) {
            synchronized (this) {
                if (estado == null) {
                    estado = carregar();
                }
                e = estado;
            }
        }
        return e.props();
    }

    /**
     * A conexão com o AD, aberta na primeira vez que alguém precisa dela.
     *
     * @return vazio com o AD desligado
     * @throws AdIndisponivelException se o AD estiver ligado mas não responder; a próxima chamada tenta de novo
     */
    public Optional<AdDiretorio> diretorio() {
        AdProperties p = atual();
        if (!p.isHabilitado()) {
            return Optional.empty();
        }
        synchronized (this) {
            if (diretorio == null) {
                try {
                    diretorio = new AdDiretorio(p);
                    ultimoErro = null;
                } catch (RuntimeException ex) {
                    ultimoErro = explicar(ex);
                    throw ex;
                }
            }
            return Optional.of(diretorio);
        }
    }

    /** Confere se a configuração mudou (em outra instância ou direto no banco). */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void conferirMudanca() {
        Estado e = estado;
        if (e != null && !Objects.equals(e.versao(), versaoNoBanco())) {
            recarregar();
        }
    }

    public Tela tela() {
        Map<String, Object> linha = linha();
        AdProperties p = atual();
        Dados d = new Dados();
        d.setHabilitado(p.isHabilitado());
        List<String> hosts = new ArrayList<>();
        Integer porta = null;
        boolean ldaps = true;
        if (p.getUrl() != null) {
            for (String u : p.getUrl().split(",")) {
                if (u.isBlank()) {
                    continue;
                }
                try {
                    java.net.URI uri = java.net.URI.create(u.trim());
                    hosts.add(uri.getHost());
                    ldaps = "ldaps".equalsIgnoreCase(uri.getScheme());
                    porta = uri.getPort() > 0 ? uri.getPort() : null;
                } catch (IllegalArgumentException ignorada) {
                    hosts.add(u.trim());
                }
            }
        }
        boolean startTls = !ldaps && p.isStartTls();
        boolean semCifra = !ldaps && !startTls && p.isSemCriptografia();
        d.setServidores(String.join(", ", hosts));
        d.setSeguranca(startTls ? "starttls" : semCifra ? "nenhuma" : "ldaps");
        d.setPorta(porta != null ? porta : (startTls || semCifra ? 389 : 636));
        d.setCertificadoCa(p.getCertificadoCa());
        d.setBindDn(p.getBindDn());
        d.setBaseUsuarios(p.getBaseUsuarios());
        d.setFiltro(filtroAdicional(p.getFiltroUsuarios()));
        d.setOuRaiz(p.getOuRaiz());
        d.setEspelharOus(p.isEspelharOus());
        d.setDeptRaizId(p.getDeptRaizId());
        d.setPerfilPadrao(p.getPerfilPadrao());
        d.setGrupoAprovadores(p.getGrupoAprovadores());
        d.setGruposPerfis(new LinkedHashMap<>(p.getGruposPerfis()));
        d.setDesativarAusentes(p.isDesativarAusentes());
        boolean conectado;
        synchronized (this) {
            conectado = diretorio != null;
        }
        return new Tela(d, p.getBindSenha() != null && !p.getBindSenha().isEmpty(), linha == null ? "ambiente" : "tela",
                linha == null ? null : (String) linha.get("updater"), linha == null ? null : (Date) linha.get("update_time"),
                conectado, ultimoErro);
    }

    // ------------------------------------------------------------------ gravação

    /**
     * @param novaSenha vazia mantém a senha salva
     * @return o que mudou, para a auditoria (sem valores de senha)
     */
    public List<String> salvar(Dados d, String novaSenha, String updater) {
        AdProperties antes = atual();
        AdProperties depois = aplicar(antes, d, novaSenha);
        validar(depois);
        String senhaCifrada = null;
        if (novaSenha != null && !novaSenha.isEmpty()) {
            senhaCifrada = cifrador.cifrar(novaSenha, CONTEXTO_SENHA);
        } else {
            Map<String, Object> linha = linha();
            if (linha != null) {
                senhaCifrada = (String) linha.get("bind_senha");
            } else if (ambiente.getBindSenha() != null && !ambiente.getBindSenha().isEmpty()) {
                // primeira gravação: a senha que veio da variável de ambiente passa a ficar no banco
                senhaCifrada = cifrador.cifrar(ambiente.getBindSenha(), CONTEXTO_SENHA);
            }
        }
        jdbcTemplate.update("""
                        INSERT INTO identidade.ad_config (id, habilitado, servidores, porta, seguranca, certificado_ca, bind_dn, bind_senha,
                            base_usuarios, filtro, ou_raiz, espelhar_ous, dept_raiz_id, perfil_padrao, grupo_aprovadores, grupos_perfis,
                            desativar_ausentes, updater, update_time)
                        VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                        ON CONFLICT (id) DO UPDATE SET habilitado = EXCLUDED.habilitado, servidores = EXCLUDED.servidores,
                            porta = EXCLUDED.porta, seguranca = EXCLUDED.seguranca, certificado_ca = EXCLUDED.certificado_ca,
                            bind_dn = EXCLUDED.bind_dn, bind_senha = EXCLUDED.bind_senha, base_usuarios = EXCLUDED.base_usuarios,
                            filtro = EXCLUDED.filtro, ou_raiz = EXCLUDED.ou_raiz, espelhar_ous = EXCLUDED.espelhar_ous,
                            dept_raiz_id = EXCLUDED.dept_raiz_id, perfil_padrao = EXCLUDED.perfil_padrao,
                            grupo_aprovadores = EXCLUDED.grupo_aprovadores, grupos_perfis = EXCLUDED.grupos_perfis,
                            desativar_ausentes = EXCLUDED.desativar_ausentes, updater = EXCLUDED.updater, update_time = now()""",
                d.isHabilitado(), limpar(d.getServidores()), d.getPorta(), seguranca(d), limpar(d.getCertificadoCa()),
                limpar(d.getBindDn()), senhaCifrada, limpar(d.getBaseUsuarios()), limpar(d.getFiltro()), limpar(d.getOuRaiz()),
                d.isEspelharOus(), d.getDeptRaizId(), limpar(d.getPerfilPadrao()), limpar(d.getGrupoAprovadores()),
                JsonUtils.toJsonString(semVazios(d.getGruposPerfis())), d.isDesativarAusentes(), updater);
        recarregar();
        return diferencas(antes, atual(), novaSenha != null && !novaSenha.isEmpty());
    }

    /**
     * Testa a configuração da tela, sem salvar: conecta com a conta de serviço, conta quem o filtro deixa entrar e, se vier
     * um login, confere a senha dele.
     */
    public Teste testar(Dados d, String novaSenha, String login, String senhaUsuario) {
        List<String> etapas = new ArrayList<>();
        AdProperties p;
        try {
            p = aplicar(atual(), d, novaSenha);
            p.setHabilitado(true);
            validar(p);
        } catch (IllegalArgumentException e) {
            return new Teste(false, etapas, e.getMessage());
        }
        try (AdDiretorio teste = new AdDiretorio(p)) {
            etapas.add("Conectou em " + p.getUrl() + " e a conta de serviço entrou"
                    + (p.isSemCriptografia() ? " (sem criptografia: as senhas passam abertas pela rede)" : ""));
            Map.Entry<Integer, List<String>> contagem = teste.contarUsuarios(LIMITE_CONTAGEM);
            if (contagem.getKey() == 0) {
                return new Teste(false, etapas, "Nenhuma conta encontrada com esse BaseDN e filtro");
            }
            etapas.add((contagem.getKey() >= LIMITE_CONTAGEM ? "Mais de " : "") + contagem.getKey()
                    + " conta(s) podem entrar. Exemplos: " + String.join(", ", contagem.getValue()));
            if (login != null && !login.isBlank()) {
                String sam = loginSemDominio(login);
                Optional<AdUsuario> conta = teste.buscarPorLogin(sam);
                if (conta.isEmpty()) {
                    return new Teste(false, etapas, "O login " + sam + " não existe na base ou não passa no filtro");
                }
                AdUsuario u = conta.get();
                etapas.add("Achou " + u.getLogin() + " (" + Optional.ofNullable(u.getNome()).orElse("") + "), "
                        + (u.isAtivo() ? "ativo" : "DESATIVADO no AD") + ", " + u.getGrupos().size() + " grupo(s)"
                        + (u.getCaminhoOus().isEmpty() ? "" : ", OU " + String.join(" / ", u.getCaminhoOus())));
                if (senhaUsuario != null && !senhaUsuario.isEmpty()) {
                    if (teste.autenticar(sam, senhaUsuario).isEmpty()) {
                        return new Teste(false, etapas, "O AD recusou a senha de " + sam + " (senha errada, conta bloqueada ou expirada)");
                    }
                    etapas.add("A senha de " + sam + " foi aceita");
                }
            }
            return new Teste(true, etapas, null);
        } catch (RuntimeException e) {
            log.info("[testar][falhou: {}]", e.toString());
            return new Teste(false, etapas, explicar(e));
        }
    }

    /** Certificados que o servidor apresenta, para a tela mostrar e o administrador decidir se confia. */
    public List<CertificadoInfo> certificados(String servidor, Integer porta, String seguranca) {
        String host = servidor == null ? "" : servidor.split(",")[0].trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Informe o servidor");
        }
        if ("nenhuma".equals(seguranca)) {
            throw new IllegalArgumentException("Sem criptografia não há certificado a buscar");
        }
        boolean startTls = "starttls".equals(seguranca);
        int p = porta != null ? porta : (startTls ? 389 : 636);
        try {
            List<CertificadoInfo> r = new ArrayList<>();
            for (X509Certificate c : AdDiretorio.certificadosDoServidor(host, p, startTls, ambiente.getTimeoutConexao())) {
                r.add(new CertificadoInfo(c.getSubjectX500Principal().getName(), c.getIssuerX500Principal().getName(),
                        c.getNotAfter(), impressao(c), c.getBasicConstraints() >= 0, pem(c)));
            }
            return r;
        } catch (AdIndisponivelException e) {
            throw new IllegalArgumentException(explicar(e));
        }
    }

    // ------------------------------------------------------------------ interno

    private synchronized void recarregar() {
        estado = carregar();
        if (diretorio != null) {
            try {
                diretorio.close();
            } catch (RuntimeException e) {
                log.warn("[recarregar][falha ao fechar a conexão antiga com o AD]", e);
            }
            diretorio = null;
        }
        ultimoErro = null;
        log.info("[recarregar][configuração do AD aplicada: habilitado={}, url={}]", estado.props().isHabilitado(), estado.props().getUrl());
    }

    private Estado carregar() {
        Map<String, Object> l = linha();
        AdProperties p = ambiente.copia();
        if (l == null) {
            return new Estado(p, null);
        }
        Dados d = new Dados();
        d.setHabilitado(Boolean.TRUE.equals(l.get("habilitado")));
        d.setServidores((String) l.get("servidores"));
        d.setPorta(l.get("porta") == null ? null : ((Number) l.get("porta")).intValue());
        d.setSeguranca((String) l.get("seguranca"));
        d.setCertificadoCa((String) l.get("certificado_ca"));
        d.setBindDn((String) l.get("bind_dn"));
        d.setBaseUsuarios((String) l.get("base_usuarios"));
        d.setFiltro((String) l.get("filtro"));
        d.setOuRaiz((String) l.get("ou_raiz"));
        d.setEspelharOus(!Boolean.FALSE.equals(l.get("espelhar_ous")));
        d.setDeptRaizId(l.get("dept_raiz_id") == null ? null : ((Number) l.get("dept_raiz_id")).longValue());
        d.setPerfilPadrao((String) l.get("perfil_padrao"));
        d.setGrupoAprovadores((String) l.get("grupo_aprovadores"));
        String grupos = (String) l.get("grupos_perfis");
        d.setGruposPerfis(grupos == null || grupos.isBlank() ? new LinkedHashMap<>()
                : JsonUtils.parseObject(grupos, new TypeReference<LinkedHashMap<String, String>>() {
        }));
        d.setDesativarAusentes(!Boolean.FALSE.equals(l.get("desativar_ausentes")));
        String senha = null;
        String cifrada = (String) l.get("bind_senha");
        if (cifrada != null && !cifrada.isEmpty()) {
            try {
                senha = cifrador.decifrar(cifrada, CONTEXTO_SENHA);
            } catch (RuntimeException e) {
                log.error("[carregar][não foi possível decifrar a senha da conta de serviço do AD: a chave-mestra mudou?]", e);
            }
        }
        AdProperties efetiva = aplicar(p, d, null);
        efetiva.setBindSenha(senha);
        return new Estado(efetiva, (Timestamp) l.get("update_time"));
    }

    /** Sobre uma cópia de {@code base}, aplica os campos da tela. {@code novaSenha} vazia mantém a da base. */
    static AdProperties aplicar(AdProperties base, Dados d, String novaSenha) {
        AdProperties p = base.copia();
        p.setHabilitado(d.isHabilitado());
        String seguranca = seguranca(d);
        boolean startTls = "starttls".equals(seguranca);
        boolean semCifra = "nenhuma".equals(seguranca);
        String esquema = startTls || semCifra ? "ldap" : "ldaps";
        int porta = d.getPorta() != null && d.getPorta() > 0 ? d.getPorta() : (startTls || semCifra ? 389 : 636);
        List<String> urls = new ArrayList<>();
        for (String s : Optional.ofNullable(d.getServidores()).orElse("").split("[,;\\s]+")) {
            String h = s.trim().replaceFirst("^(?i)ldaps?://", "").replaceFirst(":\\d+/?$", "").replaceFirst("/$", "");
            if (!h.isEmpty()) {
                urls.add(esquema + "://" + h + ":" + porta);
            }
        }
        p.setUrl(urls.isEmpty() ? null : String.join(",", urls));
        p.setStartTls(startTls);
        p.setSemCriptografia(semCifra);
        p.setCertificadoCa(limpar(d.getCertificadoCa()));
        if (p.getCertificadoCa() != null) {
            p.setConfiarEmQualquerCertificado(false);
        }
        p.setBindDn(limpar(d.getBindDn()));
        if (novaSenha != null && !novaSenha.isEmpty()) {
            p.setBindSenha(novaSenha);
        }
        p.setBaseUsuarios(limpar(d.getBaseUsuarios()));
        String extra = limpar(d.getFiltro());
        p.setFiltroUsuarios(extra == null ? FILTRO_PADRAO : "(&(objectCategory=person)(objectClass=user)" + extra + ")");
        p.setOuRaiz(limpar(d.getOuRaiz()));
        p.setEspelharOus(d.isEspelharOus());
        if (d.getDeptRaizId() != null) {
            p.setDeptRaizId(d.getDeptRaizId());
        }
        p.setPerfilPadrao(limpar(d.getPerfilPadrao()));
        p.setGrupoAprovadores(limpar(d.getGrupoAprovadores()));
        p.setGruposPerfis(semVazios(d.getGruposPerfis()));
        p.setDesativarAusentes(d.isDesativarAusentes());
        return p;
    }

    /** O que a tela mostra como "filtro": o filtro completo sem o pedaço fixo de "é uma pessoa". */
    static String filtroAdicional(String completo) {
        if (completo == null || completo.isBlank() || completo.equals(FILTRO_PADRAO)) {
            return null;
        }
        String prefixo = "(&(objectCategory=person)(objectClass=user)";
        if (completo.startsWith(prefixo) && completo.endsWith(")")) {
            String resto = completo.substring(prefixo.length(), completo.length() - 1);
            if (!resto.isEmpty()) {
                return resto;
            }
        }
        return completo;
    }

    private static void validar(AdProperties p) {
        if (!p.isHabilitado()) {
            return;
        }
        if (p.getUrl() == null) {
            throw new IllegalArgumentException("Informe o servidor do AD");
        }
        if (p.getBindDn() == null) {
            throw new IllegalArgumentException("Informe a conta de serviço (RootDN)");
        }
        if (p.getBindSenha() == null || p.getBindSenha().isEmpty()) {
            throw new IllegalArgumentException("Informe a senha da conta de serviço");
        }
        if (p.getBaseUsuarios() == null) {
            throw new IllegalArgumentException("Informe o BaseDN");
        }
        try {
            com.unboundid.ldap.sdk.Filter.create(p.getFiltroUsuarios());
        } catch (LDAPException e) {
            throw new IllegalArgumentException("O filtro não é um filtro LDAP válido: confira os parênteses");
        }
    }

    /** "PMA\joao" e "joao@pma.local" viram "joao". */
    static String loginSemDominio(String login) {
        String l = login.trim();
        int barra = l.lastIndexOf('\\');
        if (barra >= 0) {
            l = l.substring(barra + 1);
        }
        int arroba = l.indexOf('@');
        return arroba > 0 ? l.substring(0, arroba) : l;
    }

    /** Traduz a falha do LDAP para algo que o administrador consegue resolver. */
    public static String explicar(Throwable e) {
        List<String> mensagens = new ArrayList<>();
        ResultCode codigo = null;
        String diagnostico = null;
        for (Throwable t = e; t != null && mensagens.size() < 8; t = t.getCause()) {
            if (t.getMessage() != null) {
                mensagens.add(t.getMessage());
            }
            if (t instanceof LDAPException le && codigo == null) {
                codigo = le.getResultCode();
                diagnostico = le.getDiagnosticMessage();
            }
            if (t instanceof IllegalStateException && t.getCause() == null && t.getMessage() != null) {
                return t.getMessage();
            }
        }
        String tudo = String.join(" | ", mensagens);
        String baixo = tudo.toLowerCase(Locale.ROOT);
        if (baixo.contains("error initializing ssl/tls")) {
            return "O DC aceitou a conexão mas não tem certificado para ligar o TLS. Instale um certificado de servidor no DC "
                    + "ou, se a ligação fica toda na rede interna, escolha a segurança \"Sem criptografia\".";
        }
        if (baixo.contains("pkix") || baixo.contains("certificate") || baixo.contains("certificado")
                || baixo.contains("trust") && baixo.contains("ssl")) {
            return "O certificado do servidor não é confiável. Use \"Buscar certificado do servidor\" e confirme a impressão digital, "
                    + "ou cole o certificado da CA do AD.";
        }
        if (codigo == ResultCode.INVALID_CREDENTIALS) {
            return "O AD recusou a conta de serviço ou a senha (RootDN/senha)" + (diagnostico == null ? "" : ": " + diagnostico);
        }
        if (codigo == ResultCode.NO_SUCH_OBJECT) {
            return "O BaseDN não existe no AD. Confira a grafia, ex.: OU=Usuarios,DC=pma,DC=local";
        }
        if (codigo == ResultCode.FILTER_ERROR) {
            return "O filtro foi recusado pelo AD: confira os parênteses";
        }
        if (codigo == ResultCode.CONFIDENTIALITY_REQUIRED || codigo == ResultCode.STRONG_AUTH_REQUIRED) {
            return "O AD exige conexão segura: use LDAPS (636) ou StartTLS (389)";
        }
        if (codigo == ResultCode.CONNECT_ERROR || codigo == ResultCode.SERVER_DOWN || codigo == ResultCode.TIMEOUT
                || baixo.contains("connect") || baixo.contains("timed out") || baixo.contains("unknownhost")) {
            return "Não foi possível conectar ao servidor. Confira nome/IP e porta, e se o servidor do CO-TI alcança o DC "
                    + "(VPN, rota ou firewall liberando a porta).";
        }
        return tudo.isEmpty() ? e.getClass().getSimpleName() : tudo;
    }

    private static List<String> diferencas(AdProperties a, AdProperties b, boolean senhaTrocada) {
        List<String> r = new ArrayList<>();
        comparar(r, "habilitado", a.isHabilitado(), b.isHabilitado());
        comparar(r, "servidor", a.getUrl(), b.getUrl());
        comparar(r, "sem criptografia", a.isSemCriptografia(), b.isSemCriptografia());
        comparar(r, "certificado", a.getCertificadoCa() == null, b.getCertificadoCa() == null);
        comparar(r, "conta de serviço", a.getBindDn(), b.getBindDn());
        comparar(r, "BaseDN", a.getBaseUsuarios(), b.getBaseUsuarios());
        comparar(r, "filtro", a.getFiltroUsuarios(), b.getFiltroUsuarios());
        comparar(r, "OU raiz", a.getOuRaiz(), b.getOuRaiz());
        comparar(r, "espelhar OUs", a.isEspelharOus(), b.isEspelharOus());
        comparar(r, "departamento raiz", a.getDeptRaizId(), b.getDeptRaizId());
        comparar(r, "perfil padrão", a.getPerfilPadrao(), b.getPerfilPadrao());
        comparar(r, "grupo de aprovadores", a.getGrupoAprovadores(), b.getGrupoAprovadores());
        comparar(r, "grupos e perfis", a.getGruposPerfis(), b.getGruposPerfis());
        comparar(r, "desativar ausentes", a.isDesativarAusentes(), b.isDesativarAusentes());
        if (senhaTrocada) {
            r.add("senha da conta de serviço");
        }
        return r;
    }

    private static void comparar(List<String> r, String nome, Object a, Object b) {
        if (!Objects.equals(a, b)) {
            r.add(nome);
        }
    }

    private Map<String, Object> linha() {
        List<Map<String, Object>> l = jdbcTemplate.queryForList("SELECT * FROM identidade.ad_config WHERE id = 1");
        return l.isEmpty() ? null : l.get(0);
    }

    private Timestamp versaoNoBanco() {
        List<Timestamp> l = jdbcTemplate.queryForList("SELECT update_time FROM identidade.ad_config WHERE id = 1", Timestamp.class);
        return l.isEmpty() ? null : l.get(0);
    }

    private static String seguranca(Dados d) {
        String s = d.getSeguranca() == null ? "" : d.getSeguranca().toLowerCase(Locale.ROOT);
        return s.equals("starttls") || s.equals("nenhuma") ? s : "ldaps";
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Map<String, String> semVazios(Map<String, String> m) {
        Map<String, String> r = new LinkedHashMap<>();
        if (m != null) {
            m.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null && !v.isBlank()) {
                    r.put(k.trim(), v.trim());
                }
            });
        }
        return r;
    }

    private static String impressao(X509Certificate c) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
            StringJoiner j = new StringJoiner(":");
            for (byte b : h) {
                j.add(String.format("%02X", b));
            }
            return j.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String pem(X509Certificate c) {
        try {
            return "-----BEGIN CERTIFICATE-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(c.getEncoded())
                    + "\n-----END CERTIFICATE-----\n";
        } catch (Exception e) {
            return "";
        }
    }

    @PreDestroy
    public synchronized void fechar() {
        if (diretorio != null) {
            diretorio.close();
            diretorio = null;
        }
    }

}
