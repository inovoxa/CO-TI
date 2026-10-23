package cn.iocoder.yudao.module.identidade.ad;

import cn.iocoder.yudao.module.identidade.config.AdProperties;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import com.unboundid.util.ssl.TrustStoreTrustManager;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    private static LDAPConnectionPool criarPool(AdProperties p) {
        try {
            TrustManager confianca = null;
            if (p.isConfiarEmQualquerCertificado()) {
                log.warn("[AD][coti.ad.confiar-em-qualquer-certificado=true: use só em laboratório]");
                confianca = new TrustAllTrustManager();
            } else if (p.getTruststore() != null && !p.getTruststore().isBlank()) {
                confianca = new TrustStoreTrustManager(p.getTruststore(),
                        p.getTruststoreSenha() == null ? null : p.getTruststoreSenha().toCharArray(), null, true);
            }
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
            if (!ldaps && !p.isStartTls()) {
                // a senha do usuário trafega no bind: sem TLS ela iria em texto puro pela rede
                throw new IllegalStateException("coti.ad.url precisa ser ldaps:// ou usar coti.ad.start-tls=true");
            }
            ServerSet servidores = new FailoverServerSet(hosts.toArray(new String[0]),
                    portas.stream().mapToInt(Integer::intValue).toArray(),
                    ldaps ? ssl.getSocketFactory() : null, opcoes);
            PostConnectProcessor startTls = ldaps ? null : new StartTLSPostConnectProcessor(ssl);
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
