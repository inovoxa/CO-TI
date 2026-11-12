package cn.iocoder.yudao.module.chat.service;

import cn.iocoder.yudao.framework.redis.core.ExecucaoUnica;
import cn.iocoder.yudao.module.system.service.retencao.RetencaoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Apaga do disco os anexos do chat mais velhos que o prazo da tela Infraestrutura → Visão geral ("Anexos do chat", em horas).
 * A linha fica marcada como expirada, para a conversa mostrar "anexo expirado" em vez de sumir com a mensagem. Anexos enviados e
 * nunca usados numa mensagem (a pessoa desistiu) somem por inteiro depois de um dia. Roda de hora em hora, então o atraso
 * máximo em relação ao prazo é de uma hora.
 */
@Slf4j
@Component
public class ChatAnexosLimpezaJob {

    private static final int LOTE = 500;
    private static final Duration ORFAO = Duration.ofHours(24);

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private RetencaoService retencao;
    @Resource
    private ExecucaoUnica execucaoUnica;

    @Value("${coti.chat.pasta-arquivos:arquivos/chat}")
    private String pastaArquivos;

    @Scheduled(cron = "${coti.chat.limpeza-anexos-cron:0 17 * * * *}")
    public void limpar() {
        execucaoUnica.executar("chat:limpeza-anexos", Duration.ofMinutes(50), this::limparAgora);
    }

    void limparAgora() {
        LocalDateTime agora = LocalDateTime.now();
        int horas = retencao.valor(RetencaoService.CHAT_ANEXOS_HORAS);
        long bytes = 0;
        int expirados = 0;
        if (horas > 0) {
            Timestamp antes = Timestamp.valueOf(agora.minusHours(horas));
            List<Map<String, Object>> lote;
            do {
                lote = jdbcTemplate.queryForList("SELECT id, caminho, tamanho FROM chat.anexo WHERE expirado_em IS NULL "
                        + "AND mensagem_id IS NOT NULL AND create_time < ? ORDER BY id LIMIT " + LOTE, antes);
                for (Map<String, Object> a : lote) {
                    apagarArquivo((String) a.get("caminho"));
                    jdbcTemplate.update("UPDATE chat.anexo SET expirado_em = now(), update_time = now() WHERE id = ?", a.get("id"));
                    bytes += ((Number) a.get("tamanho")).longValue();
                    expirados++;
                }
            } while (lote.size() == LOTE);
        }
        // enviados e nunca colocados numa mensagem
        List<Map<String, Object>> orfaos = jdbcTemplate.queryForList("SELECT id, caminho, tamanho FROM chat.anexo WHERE mensagem_id IS NULL "
                + "AND create_time < ? LIMIT 5000", Timestamp.valueOf(agora.minus(ORFAO)));
        for (Map<String, Object> a : orfaos) {
            apagarArquivo((String) a.get("caminho"));
            jdbcTemplate.update("DELETE FROM chat.anexo WHERE id = ?", a.get("id"));
            bytes += ((Number) a.get("tamanho")).longValue();
        }
        if (expirados + orfaos.size() > 0) {
            log.info("[limpeza de anexos do chat] {} expirado(s), {} sem mensagem; {} MB liberados", expirados, orfaos.size(),
                    bytes / (1024 * 1024));
        }
    }

    private void apagarArquivo(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return;
        }
        Path base = Path.of(pastaArquivos).toAbsolutePath().normalize();
        Path arquivo = base.resolve(caminho).normalize();
        if (!arquivo.startsWith(base)) {
            return;
        }
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            log.warn("[limpeza de anexos do chat] não foi possível apagar {}", arquivo, e);
        }
    }

}
