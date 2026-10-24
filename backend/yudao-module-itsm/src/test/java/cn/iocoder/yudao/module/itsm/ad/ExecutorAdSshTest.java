package cn.iocoder.yudao.module.itsm.ad;

import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ExecutorAdSshTest {

    private static final String SCRIPT = "C:\\Scripts\\Executar_AD_Automatico.ps1";

    @Test
    void dadosDoFormularioNuncaEntramCruNoComando() {
        String perigoso = "{\"nome_completo\":\"Ana\\\"; Remove-Item C:\\\\ -Recurse; \\\"\"}";
        String cmd = ExecutorAdSsh.comando(SCRIPT, "Criar", perigoso);
        assertFalse(cmd.contains("Remove-Item"), "o JSON vai em Base64");
        String b64 = cmd.substring(cmd.lastIndexOf(' ') + 1);
        assertEquals(perigoso, new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8));
        assertTrue(cmd.startsWith("powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"" + SCRIPT + "\" -Acao Criar"));
    }

    @Test
    void acaoEScriptSoDeListaFechada() {
        assertThrows(IllegalArgumentException.class, () -> ExecutorAdSsh.comando(SCRIPT, "Criar; whoami", "{}"));
        assertThrows(IllegalArgumentException.class, () -> ExecutorAdSsh.comando("C:\\x.ps1\"; whoami", "Reset", "{}"));
        assertThrows(IllegalArgumentException.class, () -> ExecutorAdSsh.comando("/tmp/x.ps1", "Reset", "{}"));
    }

    @Test
    void semImpressaoDigitalNaoConfigura() {
        ItsmProperties.Ssh s = new ItsmProperties.Ssh();
        s.setHost("dc01");
        assertThrows(IllegalStateException.class, () -> new ExecutorAdSsh(s));
    }

    @Test
    void pegaOJsonMesmoComAvisosAntes() {
        assertEquals("{\"sucesso\":true}", ExecutorAdSsh.ultimaLinhaJson("AVISO: módulo carregado\r\n{\"sucesso\":true}\r\n"));
        assertNull(ExecutorAdSsh.ultimaLinhaJson("erro qualquer"));
    }

}
