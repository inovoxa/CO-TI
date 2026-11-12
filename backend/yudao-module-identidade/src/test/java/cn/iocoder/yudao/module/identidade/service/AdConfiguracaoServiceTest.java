package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.module.identidade.ad.AdIndisponivelException;
import cn.iocoder.yudao.module.identidade.config.AdProperties;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdConfiguracaoServiceTest {

    private static AdConfiguracaoService.Dados dados() {
        AdConfiguracaoService.Dados d = new AdConfiguracaoService.Dados();
        d.setHabilitado(true);
        d.setServidores("10.0.0.5, dc02.pma.local");
        d.setSeguranca("ldaps");
        d.setBindDn("svc.coti@pma.local");
        d.setBaseUsuarios("DC=pma,DC=local");
        d.setEspelharOus(false);
        d.setDesativarAusentes(true);
        return d;
    }

    @Test
    void montaUmaUrlPorServidorComAPortaEOEsquemaDaSeguranca() {
        AdProperties p = AdConfiguracaoService.aplicar(new AdProperties(), dados(), null);
        assertEquals("ldaps://10.0.0.5:636,ldaps://dc02.pma.local:636", p.getUrl());
        assertFalse(p.isStartTls());

        AdConfiguracaoService.Dados d = dados();
        d.setSeguranca("starttls");
        d.setServidores("ldap://10.0.0.5:389/");
        p = AdConfiguracaoService.aplicar(new AdProperties(), d, null);
        assertEquals("ldap://10.0.0.5:389", p.getUrl());
        assertTrue(p.isStartTls());
        assertFalse(p.isSemCriptografia());

        d.setSeguranca("nenhuma");
        d.setPorta(null);
        d.setServidores("10.0.100.124");
        p = AdConfiguracaoService.aplicar(new AdProperties(), d, null);
        assertEquals("ldap://10.0.100.124:389", p.getUrl());
        assertFalse(p.isStartTls());
        assertTrue(p.isSemCriptografia());
    }

    @Test
    void dcSemCertificadoExplicaOQueFazer() {
        LDAPException e = new LDAPException(ResultCode.UNAVAILABLE,
                "00000000: LdapErr: DSID-0C0912BA, comment: Error initializing SSL/TLS, data 0, v3839");
        String msg = AdConfiguracaoService.explicar(new AdIndisponivelException("Não foi possível conectar ao AD", e));
        assertTrue(msg.contains("Sem criptografia"), msg);
    }

    @Test
    void filtroDaTelaESomadoAoDePessoasEVoltaIgualParaATela() {
        AdConfiguracaoService.Dados d = dados();
        d.setFiltro("(memberOf=CN=GG-Portal,OU=Grupos,DC=pma,DC=local)");
        AdProperties p = AdConfiguracaoService.aplicar(new AdProperties(), d, null);
        assertEquals("(&(objectCategory=person)(objectClass=user)(memberOf=CN=GG-Portal,OU=Grupos,DC=pma,DC=local))",
                p.getFiltroUsuarios());
        assertEquals(d.getFiltro(), AdConfiguracaoService.filtroAdicional(p.getFiltroUsuarios()));

        d.setFiltro("  ");
        assertEquals(AdConfiguracaoService.FILTRO_PADRAO, AdConfiguracaoService.aplicar(new AdProperties(), d, null).getFiltroUsuarios());
        assertNull(AdConfiguracaoService.filtroAdicional(AdConfiguracaoService.FILTRO_PADRAO));
        assertEquals("(objectClass=user)", AdConfiguracaoService.filtroAdicional("(objectClass=user)"));
    }

    @Test
    void senhaVaziaMantemADaBaseENaoMexeNaOriginal() {
        AdProperties base = new AdProperties();
        base.setBindSenha("antiga");
        base.getGruposPerfis().put("GG-NOC", "noc");
        AdConfiguracaoService.Dados d = dados();
        d.setGruposPerfis(Map.of("GG-SD", "service_desk", " ", "x"));
        AdProperties p = AdConfiguracaoService.aplicar(base, d, "");
        assertEquals("antiga", p.getBindSenha());
        assertEquals(Map.of("GG-SD", "service_desk"), p.getGruposPerfis());
        assertEquals(Map.of("GG-NOC", "noc"), base.getGruposPerfis());
        assertEquals("nova", AdConfiguracaoService.aplicar(base, d, "nova").getBindSenha());
        assertFalse(p.isEspelharOus());
    }

    @Test
    void loginComDominioViraSoOUsuario() {
        assertEquals("joao", AdConfiguracaoService.loginSemDominio("PMA\\joao"));
        assertEquals("joao", AdConfiguracaoService.loginSemDominio(" joao@pma.local "));
        assertEquals("joao", AdConfiguracaoService.loginSemDominio("joao"));
    }

    @Test
    void explicaAsFalhasMaisComuns() {
        assertTrue(AdConfiguracaoService.explicar(new AdIndisponivelException("x",
                new LDAPException(ResultCode.INVALID_CREDENTIALS, "80090308: data 52e"))).contains("conta de serviço"));
        assertTrue(AdConfiguracaoService.explicar(new AdIndisponivelException("x",
                new LDAPException(ResultCode.CONNECT_ERROR, "An error occurred while attempting to connect"))).contains("conectar"));
        assertTrue(AdConfiguracaoService.explicar(new AdIndisponivelException("x",
                new javax.net.ssl.SSLHandshakeException("PKIX path building failed"))).contains("certificado"));
        assertEquals("Informe o servidor do AD", AdConfiguracaoService.explicar(new AdIndisponivelException("x",
                new IllegalStateException("Informe o servidor do AD"))));
    }

}
