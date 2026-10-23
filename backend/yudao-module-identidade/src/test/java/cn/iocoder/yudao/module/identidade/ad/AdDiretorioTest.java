package cn.iocoder.yudao.module.identidade.ad;

import cn.iocoder.yudao.module.identidade.config.AdProperties;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Servidor LDAP em memória com a forma de um AD: OUs aninhadas, sAMAccountName, memberOf e userAccountControl.
 */
class AdDiretorioTest {

    private static final String BASE = "DC=empresa,DC=local";
    private static InMemoryDirectoryServer servidor;
    private static AdDiretorio diretorio;

    @BeforeAll
    static void subir() throws Exception {
        InMemoryDirectoryServerConfig cfg = new InMemoryDirectoryServerConfig(BASE);
        cfg.setSchema(null); // atributos do AD (sAMAccountName, memberOf...) não estão no schema padrão
        cfg.addAdditionalBindCredentials("CN=svc-coti,DC=empresa,DC=local", "senha-servico");
        servidor = new InMemoryDirectoryServer(cfg);
        servidor.startListening();
        servidor.add("dn: " + BASE, "objectClass: domain", "dc: empresa");
        servidor.add("dn: OU=Sede," + BASE, "objectClass: organizationalUnit", "ou: Sede");
        servidor.add("dn: OU=TI,OU=Sede," + BASE, "objectClass: organizationalUnit", "ou: TI");
        servidor.add("dn: OU=Suporte,OU=TI,OU=Sede," + BASE, "objectClass: organizationalUnit", "ou: Suporte");
        conta("ana.souza", "CN=Ana Souza,OU=Suporte,OU=TI,OU=Sede," + BASE, "Senha#Forte2026", 512,
                "CN=GG-TI-NOC,OU=Grupos," + BASE);
        conta("bruno", "CN=Bruno Lima,OU=Sede," + BASE, "outra-senha", 514); // 514 = 512 + conta desativada
        for (int i = 0; i < 7; i++) {
            conta("lote" + i, "CN=Lote " + i + ",OU=TI,OU=Sede," + BASE, "x", 512);
        }

        AdProperties props = new AdProperties();
        props.setBaseUsuarios(BASE);
        props.setOuRaiz("OU=Sede," + BASE);
        props.setFiltroUsuarios("(objectClass=user)");
        props.setTamanhoPagina(3); // força várias páginas na listagem

        LDAPConnection c = servidor.getConnection();
        c.bind("CN=svc-coti,DC=empresa,DC=local", "senha-servico");
        diretorio = new AdDiretorio(props, new LDAPConnectionPool(c, 2), false);
    }

    private static void conta(String login, String dn, String senha, int uac, String... grupos) throws Exception {
        List<String> ldif = new ArrayList<>(List.of("dn: " + dn, "objectClass: top", "objectClass: user",
                "sAMAccountName: " + login, "displayName: " + dn.substring(3, dn.indexOf(',')),
                "mail: " + login + "@empresa.local", "userPassword: " + senha, "userAccountControl: " + uac));
        for (String g : grupos) {
            ldif.add("memberOf: " + g);
        }
        servidor.add(ldif.toArray(new String[0]));
    }

    @AfterAll
    static void descer() {
        diretorio.close();
        servidor.shutDown(true);
    }

    @Test
    void autenticaComSenhaCorretaELeAsOus() {
        AdUsuario ana = diretorio.autenticar("ana.souza", "Senha#Forte2026").orElseThrow();
        assertEquals("Ana Souza", ana.getNome());
        assertEquals("ana.souza@empresa.local", ana.getEmail());
        assertEquals(List.of("TI", "Suporte"), ana.getCaminhoOus());
        assertTrue(ana.getGrupos().contains("CN=GG-TI-NOC,OU=Grupos," + BASE));
        assertTrue(ana.isAtivo());
    }

    @Test
    void recusaSenhaErrada() {
        assertEquals(Optional.empty(), diretorio.autenticar("ana.souza", "Senha#Errada"));
    }

    @Test
    void senhaVaziaNuncaAutentica() {
        // no AD um bind com senha vazia "funciona" como anônimo; aqui tem que ser recusado antes
        assertEquals(Optional.empty(), diretorio.autenticar("ana.souza", ""));
        assertEquals(Optional.empty(), diretorio.autenticar("ana.souza", null));
    }

    @Test
    void loginComCaracteresDeFiltroNaoViraCuringa() {
        assertEquals(Optional.empty(), diretorio.buscarPorLogin("*"));
        assertEquals(Optional.empty(), diretorio.buscarPorLogin("ana*"));
        assertEquals(Optional.empty(), diretorio.buscarPorLogin("x)(sAMAccountName=ana.souza"));
    }

    @Test
    void contaDesativadaVemMarcada() {
        assertFalse(diretorio.buscarPorLogin("bruno").orElseThrow().isAtivo());
    }

    @Test
    void listaTodasAsContasEmPaginas() {
        List<String> logins = new ArrayList<>();
        diretorio.listarUsuarios(u -> logins.add(u.getLogin()));
        assertEquals(9, logins.size());
        assertTrue(logins.containsAll(List.of("ana.souza", "bruno", "lote0", "lote6")));
    }

    @Test
    void contaDiretoNaRaizTemCaminhoVazio() throws Exception {
        assertEquals(List.of(), AdDiretorio.caminhoOus("CN=Bruno,OU=Sede," + BASE, "OU=Sede," + BASE));
        assertEquals(List.of(), AdDiretorio.caminhoOus("CN=Fora,OU=Outra," + BASE, "OU=Sede," + BASE));
    }

    @Test
    void converteObjectGuidDoAd() {
        // GUID {01234567-89ab-cdef-0123-456789abcdef} como o AD guarda (3 primeiros campos invertidos)
        byte[] b = {0x67, 0x45, 0x23, 0x01, (byte) 0xab, (byte) 0x89, (byte) 0xef, (byte) 0xcd,
                0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef};
        assertEquals("01234567-89ab-cdef-0123-456789abcdef", AdDiretorio.guidParaTexto(b, "x"));
    }

}
