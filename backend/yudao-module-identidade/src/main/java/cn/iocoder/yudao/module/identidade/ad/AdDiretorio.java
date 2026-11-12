package cn.iocoder.yudao.module.identidade.ad;

import cn.iocoder.yudao.module.identidade.config.AdProperties;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import com.unboundid.util.ssl.TrustStoreTrustManager;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Acesso de leitura ao Active Directory pela conta de serviço, mais a verificação de senha (bind) do usuário.
 *
 * <p>Toda entrada vinda do usuário vai em filtros montados por {@link Filter#createEqualityFilter}, que escapa
 * os caracteres especiais do LDAP; o DN usado no bind é sempre o que o próprio AD devolveu na busca.
 */
@Slf4j
public class AdDiretorio implements AutoCloseable {

    /** Regra LDAP_MATCHING_RULE_IN_CHAIN do AD: resolve grupos aninhados numa consulta só. */
    private static final String REGRA_EM_CADEIA = "1.2.840.113556.1.4.1941";
    private static final int UAC_CONTA_DESATIVADA = 0x2;
    static final String[] ATRIBUTOS = {"objectGUID", "sAMAccountName", "displayName", "cn", "mail",
            "telephoneNumber", "mobile", "title", "manager", "memberOf", "userAccountControl"};

    public static final Set<String> ATRIBUTOS_DE_BUSCA = Set.of("employeeID", "employeeNumber", "mail", "sAMAccountName", "userPrincipalName");

    private final AdProperties props;
    private final LDAPConnectionPool pool;
    /** O servidor LDAP em memória dos testes não conhece a regra em cadeia; o AD real sim. */
    private final boolean gruposAninhados;

    public AdDiretorio(AdProperties props) {
        this(props, criarPool(props), true);
    }

    /** Para testes: pool já pronto (ex.: servidor em memória) e sem a consulta de grupos aninhados. */
    AdDiretorio(AdProperties props, LDAPConnectionPool pool, boolean gruposAninhados) {
        this.props = props;
        this.pool = pool;
        this.gruposAninhados = gruposAninhados;
    }

    /**
     * Confere a senha do usuário com um bind no AD.
     *
     * @return a conta, se a senha estiver certa; vazio se a conta não existir ou a senha estiver errada
     * @throws AdIndisponivelException se o AD não puder ser consultado
     */
    public Optional<AdUsuario> autenticar(String login, String senha) {
        // Bind com senha vazia é "bind não autenticado" e o AD responde sucesso: nunca pode valer como login
        if (senha == null || senha.isEmpty()) {
            return Optional.empty();
        }
        Optional<AdUsuario> conta = buscarPorLogin(login);
        if (conta.isEmpty()) {
            return Optional.empty();
        }
        try {
            pool.bindAndRevertAuthentication(new SimpleBindRequest(conta.get().getDn(), senha));
            return conta;
        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
                // inclui conta bloqueada ou expirada: o AD devolve 49 com o detalhe no diagnóstico
                log.info("[autenticar][AD recusou {}: {}]", login, e.getDiagnosticMessage());
                return Optional.empty();
            }
            throw new AdIndisponivelException("Falha ao validar a senha no AD", e);
        }
    }

    public Optional<AdUsuario> buscarPorLogin(String login) {
        Filter filtro = Filter.createANDFilter(filtroBase(), Filter.createEqualityFilter("sAMAccountName", login));
        try {
            SearchResult r = pool.search(props.getBaseUsuarios(), SearchScope.SUB, filtro, ATRIBUTOS);
            if (r.getEntryCount() != 1) {
                return Optional.empty();
            }
            return Optional.of(converter(r.getSearchEntries().get(0)));
        } catch (LDAPException e) {
            throw new AdIndisponivelException("Falha ao buscar a conta no AD", e);
        }
    }

    /**
     * Todas as contas com aquele valor, para quem precisa saber se a identificação é única. O atributo vem de uma lista fechada e o
     * valor é escapado pelo filtro.
     */
    public List<AdUsuario> buscarPorAtributo(String atributo, String valor) {
        if (!ATRIBUTOS_DE_BUSCA.contains(atributo)) {
            throw new IllegalArgumentException("Atributo de busca não permitido: " + atributo);
        }
        Filter filtro = Filter.createANDFilter(filtroBase(), Filter.createEqualityFilter(atributo, valor));
        try {
            List<AdUsuario> contas = new ArrayList<>();
            for (SearchResultEntry e : pool.search(props.getBaseUsuarios(), SearchScope.SUB, filtro, ATRIBUTOS).getSearchEntries()) {
                contas.add(converter(e));
            }
            return contas;
        } catch (LDAPException e) {
            throw new AdIndisponivelException("Falha ao buscar a conta no AD", e);
        }
    }

    /** Percorre todas as contas do filtro, página a página, sem carregar tudo na memória. */
    public void listarUsuarios(Consumer<AdUsuario> consumidor) {
        try {
            SearchRequest req = new SearchRequest(props.getBaseUsuarios(), SearchScope.SUB, filtroBase(), ATRIBUTOS);
            ASN1OctetString cookie = null;
            do {
                req.setControls(new SimplePagedResultsControl(props.getTamanhoPagina(), cookie));
                SearchResult r = pool.search(req);
                for (SearchResultEntry e : r.getSearchEntries()) {
                    consumidor.accept(converter(e));
                }
                SimplePagedResultsControl resp = SimplePagedResultsControl.get(r);
                cookie = resp != null && resp.moreResultsToReturn() ? resp.getCookie() : null;
            } while (cookie != null);
        } catch (LDAPException e) {
            throw new AdIndisponivelException("Falha ao listar contas do AD", e);
        }
    }

    private Filter filtroBase() {
        try {
            return Filter.create(props.getFiltroUsuarios());
        } catch (LDAPException e) {
            throw new IllegalStateException("coti.ad.filtro-usuarios inválido: " + props.getFiltroUsuarios(), e);
        }
    }

    AdUsuario converter(SearchResultEntry e) throws LDAPException {
        String dn = e.getDN();
        Set<String> grupos = new LinkedHashSet<>();
        String[] memberOf = e.getAttributeValues("memberOf");
        if (memberOf != null) {
            grupos.addAll(Arrays.asList(memberOf));
        }
        if (gruposAninhados) {
            Filter emCadeia = Filter.createANDFilter(Filter.createEqualityFilter("objectClass", "group"),
                    Filter.createExtensibleMatchFilter("member", REGRA_EM_CADEIA, false, dn));
            for (SearchResultEntry g : pool.search(raizDoDominio(dn), SearchScope.SUB, emCadeia, "1.1")
                    .getSearchEntries()) {
                grupos.add(g.getDN());
            }
        }
        int uac = Optional.ofNullable(e.getAttributeValueAsInteger("userAccountControl")).orElse(0);
        String nome = Optional.ofNullable(e.getAttributeValue("displayName")).orElse(e.getAttributeValue("cn"));
        String telefone = Optional.ofNullable(e.getAttributeValue("mobile")).orElse(e.getAttributeValue("telephoneNumber"));
        return AdUsuario.builder()
                .guid(guidParaTexto(e.getAttributeValueBytes("objectGUID"), dn))
                .login(e.getAttributeValue("sAMAccountName"))
                .dn(dn)
                .nome(nome)
                .email(e.getAttributeValue("mail"))
                .telefone(telefone)
                .cargo(e.getAttributeValue("title"))
                .gestorDn(e.getAttributeValue("manager"))
                .caminhoOus(caminhoOus(dn, props.getOuRaizEfetiva()))
                .grupos(grupos)
                .ativo((uac & UAC_CONTA_DESATIVADA) == 0)
                .build();
    }

    /**
     * Containers entre a raiz configurada e a conta, de cima para baixo.
     * Ex.: CN=Ana,OU=Suporte,OU=TI,OU=Sede,DC=x com raiz OU=Sede,DC=x → [TI, Suporte].
     */
    static List<String> caminhoOus(String dnConta, String dnRaiz) throws LDAPException {
        DN conta = new DN(dnConta);
        DN raiz = new DN(dnRaiz);
        List<String> caminho = new ArrayList<>();
        if (!conta.isDescendantOf(raiz, false)) {
            return caminho;
        }
        RDN[] rdns = conta.getRDNs();
        int niveisAbaixo = rdns.length - raiz.getRDNs().length;
        // rdns[0] é a própria conta; de niveisAbaixo-1 até 1 estão os containers, do mais alto ao mais baixo
        for (int i = niveisAbaixo - 1; i >= 1; i--) {
            caminho.add(rdns[i].getAttributeValues()[0]);
        }
        return caminho;
    }

    /** O objectGUID do AD vem em 16 bytes com os três primeiros campos em little-endian. */
    static String guidParaTexto(byte[] b, String dnReserva) {
        if (b == null || b.length != 16) {
            // fora do AD (ex.: servidor de teste) o DN serve de identificador
            return "dn:" + dnReserva.toLowerCase(Locale.ROOT);
        }
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        long a = bb.getInt() & 0xffffffffL;
        int b1 = bb.getShort() & 0xffff;
        int c = bb.getShort() & 0xffff;
        bb.order(ByteOrder.BIG_ENDIAN);
        int d = bb.getShort() & 0xffff;
        long e = 0;
        for (int i = 0; i < 6; i++) {
            e = (e << 8) | (bb.get() & 0xff);
        }
        return String.format("%08x-%04x-%04x-%04x-%012x", a, b1, c, d, e);
    }

    private static String raizDoDominio(String dn) throws LDAPException {
        List<RDN> dcs = new ArrayList<>();
        for (RDN r : new DN(dn).getRDNs()) {
            if (r.hasAttribute("dc")) {
                dcs.add(r);
            }
        }
        return new DN(dcs).toString();
    }

    /**
     * Conta as contas do filtro sem ler os grupos de cada uma (para o teste da tela). Para em {@code limite}.
     *
     * @return quantas achou e até 5 logins de exemplo
     */
    public Map.Entry<Integer, List<String>> contarUsuarios(int limite) {
        try {
            SearchRequest req = new SearchRequest(props.getBaseUsuarios(), SearchScope.SUB, filtroBase(), "sAMAccountName");
            ASN1OctetString cookie = null;
            int total = 0;
            List<String> exemplos = new ArrayList<>();
            do {
                req.setControls(new SimplePagedResultsControl(props.getTamanhoPagina(), cookie));
                SearchResult r = pool.search(req);
                for (SearchResultEntry e : r.getSearchEntries()) {
                    total++;
                    if (exemplos.size() < 5) {
                        exemplos.add(e.getAttributeValue("sAMAccountName"));
                    }
                }
                SimplePagedResultsControl resp = SimplePagedResultsControl.get(r);
                cookie = resp != null && resp.moreResultsToReturn() && total < limite ? resp.getCookie() : null;
            } while (cookie != null);
            return Map.entry(total, exemplos);
        } catch (LDAPException e) {
            throw new AdIndisponivelException("Falha ao listar contas do AD", e);
        }
    }

    /** Quem valida o certificado do DC: a CA colada na tela, o truststore configurado ou o padrão da JVM (nulo). */
    static TrustManager confianca(AdProperties p) throws Exception {
        if (p.isConfiarEmQualquerCertificado()) {
            log.warn("[AD][coti.ad.confiar-em-qualquer-certificado=true: use só em laboratório]");
            return new TrustAllTrustManager();
        }
        if (p.getCertificadoCa() != null && !p.getCertificadoCa().isBlank()) {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            int i = 0;
            for (Certificate c : CertificateFactory.getInstance("X.509").generateCertificates(
                    new ByteArrayInputStream(p.getCertificadoCa().trim().getBytes(StandardCharsets.US_ASCII)))) {
                ks.setCertificateEntry("ca" + i++, c);
            }
            if (i == 0) {
                throw new IllegalStateException("O certificado da CA não está no formato PEM (-----BEGIN CERTIFICATE-----)");
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            return tmf.getTrustManagers()[0];
        }
        if (p.getTruststore() != null && !p.getTruststore().isBlank()) {
            return new TrustStoreTrustManager(p.getTruststore(),
                    p.getTruststoreSenha() == null ? null : p.getTruststoreSenha().toCharArray(), null, true);
        }
        return null;
    }

    /**
     * Abre a conexão TLS sem validar nada, só para ler os certificados que o servidor apresenta. Quem decide confiar neles é o
     * administrador, na tela, depois de conferir a impressão digital.
     */
    public static List<X509Certificate> certificadosDoServidor(String host, int porta, boolean startTls, Duration timeout) {
        List<X509Certificate> cadeia = new ArrayList<>();
        X509TrustManager captura = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                cadeia.clear();
                cadeia.addAll(Arrays.asList(chain));
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        LDAPConnectionOptions opcoes = new LDAPConnectionOptions();
        opcoes.setConnectTimeoutMillis((int) timeout.toMillis());
        opcoes.setResponseTimeoutMillis(timeout.toMillis());
        try {
            SSLUtil ssl = new SSLUtil(captura);
            if (startTls) {
                try (LDAPConnection c = new LDAPConnection(opcoes, host, porta)) {
                    ExtendedResult r = c.processExtendedOperation(new StartTLSExtendedRequest(ssl.createSSLContext()));
                    if (r.getResultCode() != ResultCode.SUCCESS) {
                        throw new LDAPException(r.getResultCode(), "O servidor recusou o StartTLS: " + r.getDiagnosticMessage());
                    }
                }
            } else {
                try (LDAPConnection c = new LDAPConnection(ssl.createSSLSocketFactory(), opcoes, host, porta)) {
                    c.getRootDSE();
                }
            }
        } catch (Exception e) {
            if (cadeia.isEmpty()) {
                throw new AdIndisponivelException("Não foi possível abrir a conexão segura com " + host + ":" + porta, e);
            }
        }
        if (cadeia.isEmpty()) {
            throw new AdIndisponivelException("O servidor " + host + ":" + porta + " não apresentou certificado", null);
        }
        return cadeia;
    }

    private static LDAPConnectionPool criarPool(AdProperties p) {
        try {
            if (p.getUrl() == null || p.getUrl().isBlank()) {
                throw new IllegalStateException("Informe o servidor do AD");
            }
            TrustManager confianca = confianca(p);
            SSLContext ssl = (confianca == null ? new SSLUtil() : new SSLUtil(confianca)).createSSLContext();

            LDAPConnectionOptions opcoes = new LDAPConnectionOptions();
            opcoes.setConnectTimeoutMillis((int) p.getTimeoutConexao().toMillis());
            opcoes.setResponseTimeoutMillis(p.getTimeoutOperacao().toMillis());

            List<String> hosts = new ArrayList<>();
            List<Integer> portas = new ArrayList<>();
            boolean ldaps = false;
            for (String u : p.getUrl().split(",")) {
                URI uri = URI.create(u.trim());
                ldaps = "ldaps".equalsIgnoreCase(uri.getScheme());
                hosts.add(uri.getHost());
                portas.add(uri.getPort() > 0 ? uri.getPort() : (ldaps ? 636 : 389));
            }
            boolean semCifra = !ldaps && !p.isStartTls();
            if (semCifra && !p.isSemCriptografia()) {
                // a senha do usuário trafega no bind: sem TLS ela iria em texto puro pela rede
                throw new IllegalStateException("coti.ad.url precisa ser ldaps:// ou usar coti.ad.start-tls=true");
            }
            if (semCifra) {
                log.warn("[AD][conexão sem criptografia com {}: as senhas passam abertas pela rede]", p.getUrl());
            }
            ServerSet servidores = new FailoverServerSet(hosts.toArray(new String[0]),
                    portas.stream().mapToInt(Integer::intValue).toArray(),
                    ldaps ? ssl.getSocketFactory() : null, opcoes);
            PostConnectProcessor startTls = ldaps || semCifra ? null : new StartTLSPostConnectProcessor(ssl);
            return new LDAPConnectionPool(servidores, new SimpleBindRequest(p.getBindDn(), p.getBindSenha()),
                    1, p.getTamanhoPool(), startTls);
        } catch (Exception e) {
            throw new AdIndisponivelException("Não foi possível conectar ao AD em " + p.getUrl(), e);
        }
    }

    @Override
    public void close() {
        pool.close();
    }

}
