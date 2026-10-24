package cn.iocoder.yudao.module.itsm.ad;

import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.FingerprintVerifier;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Roda o script PowerShell no servidor Windows por SSH (o mesmo protocolo do WF14 do n8n).
 *
 * <p>Segurança:
 * <ul>
 *   <li>a impressão digital da chave do servidor é obrigatória (sem ela, nada conecta: evita homem do meio);</li>
 *   <li>a ação vem de uma lista fechada e os dados vão em Base64 (-DadosB64), então nada digitado no
 *       formulário vira parte do comando;</li>
 *   <li>autenticação por chave privada (preferida) ou senha, ambas fora do código.</li>
 * </ul>
 */
@Slf4j
public class ExecutorAdSsh implements ExecutorAd {

    static final Set<String> ACOES = Set.of("Criar", "Reset", "Reativar", "Desativar", "AcessoPasta", "Transferir", "CriarEmail");

    private final ItsmProperties.Ssh cfg;

    public ExecutorAdSsh(ItsmProperties.Ssh cfg) {
        if (cfg.getHost() == null || cfg.getHost().isBlank()) {
            throw new IllegalStateException("coti.itsm.ad.ssh.host não configurado");
        }
        if (cfg.getImpressaoDigital() == null || cfg.getImpressaoDigital().isBlank()) {
            throw new IllegalStateException("coti.itsm.ad.ssh.impressao-digital é obrigatória (ex.: SHA256:...). "
                    + "Obtenha com: ssh-keyscan -t ed25519 servidor | ssh-keygen -lf -");
        }
        this.cfg = cfg;
    }

    @Override
    public String executar(String acao, String dadosJson) throws Exception {
        String comando = comando(cfg.getScript(), acao, dadosJson);
        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(FingerprintVerifier.getInstance(cfg.getImpressaoDigital()));
            ssh.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(15));
            ssh.setTimeout((int) TimeUnit.SECONDS.toMillis(cfg.getTempoLimiteSeg()));
            ssh.connect(cfg.getHost(), cfg.getPorta());
            if (cfg.getChavePrivada() != null && !cfg.getChavePrivada().isBlank()) {
                ssh.authPublickey(cfg.getUsuario(), ssh.loadKeys(cfg.getChavePrivada()));
            } else {
                ssh.authPassword(cfg.getUsuario(), cfg.getSenha());
            }
            try (Session s = ssh.startSession()) {
                Session.Command cmd = s.exec(comando);
                ByteArrayOutputStream saida = new ByteArrayOutputStream();
                cmd.getInputStream().transferTo(saida);
                cmd.join(cfg.getTempoLimiteSeg(), TimeUnit.SECONDS);
                String texto = saida.toString(StandardCharsets.UTF_8).trim();
                // o script escreve só o JSON; pega a última linha que parece JSON (ignora avisos do PowerShell)
                String json = ultimaLinhaJson(texto);
                if (json == null) {
                    throw new java.io.IOException("O script não devolveu JSON (código " + cmd.getExitStatus() + ")");
                }
                return json;
            }
        }
    }

    static String comando(String script, String acao, String dadosJson) {
        if (!ACOES.contains(acao)) {
            throw new IllegalArgumentException("Ação não permitida: " + acao);
        }
        if (script == null || !script.matches("^[A-Za-z]:\\\\[\\w\\\\ .-]+\\.ps1$")) {
            throw new IllegalArgumentException("Caminho de script inválido: " + script);
        }
        String b64 = Base64.getEncoder().encodeToString(dadosJson.getBytes(StandardCharsets.UTF_8));
        return "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"" + script + "\" -Acao " + acao
                + " -DadosB64 " + b64;
    }

    static String ultimaLinhaJson(String texto) {
        String[] linhas = texto.split("\\R");
        for (int i = linhas.length - 1; i >= 0; i--) {
            String l = linhas[i].trim();
            if (l.startsWith("{") && l.endsWith("}")) {
                return l;
            }
        }
        return null;
    }

    @Override
    public String modo() {
        return "ssh";
    }

}
