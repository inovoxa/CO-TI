package cn.iocoder.yudao.module.infraestrutura.service;

import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.InfraVOs;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Última execução de cada rotina agendada. Fica em memória e vai para o banco em lote, uma linha por rotina: há rotinas de poucos
 * segundos, e gravar a cada execução criaria justamente o tipo de tabela que a Infraestrutura tenta controlar.
 */
@Slf4j
@Service
public class RotinaRegistro {

    private static final int MAX_ERRO = 500;

    private static final Map<String, String> ROTULOS = Map.ofEntries(
            Map.entry("AtendimentoJob.enviar", "Atendimento: enviar mensagens"),
            Map.entry("AtendimentoJob.adiadas", "Atendimento: mensagens adiadas"),
            Map.entry("AtendimentoJob.webhooks", "Atendimento: processar webhooks recebidos"),
            Map.entry("AtendimentoJob.limparWebhooks", "Atendimento: limpar webhooks antigos"),
            Map.entry("AlertaInventarioJob.executar", "Inventário: avaliar alertas"),
            Map.entry("SnmpJob.executar", "Inventário: varredura SNMP"),
            Map.entry("AutomacaoSlaJob.escalonar", "Chamados: escalonar por SLA"),
            Map.entry("AutomacaoSlaJob.fecharSolucionados", "Chamados: fechar solucionados"),
            Map.entry("SlaAvisoJob.executar", "Chamados: aviso de SLA perto de vencer"),
            Map.entry("ExecucaoAdJob.executar", "Chamados: operações no AD"),
            Map.entry("EmailJob.enviar", "E-mail: enviar"),
            Map.entry("EmailJob.coletar", "E-mail: ler caixas"),
            Map.entry("GovernancaAlertaJob.executar", "Governança: alertas de contratos e licenças"),
            Map.entry("RetencaoLogsJob.limpar", "Logs: apagar dias vencidos"),
            Map.entry("RetencaoLogsJob.limitarTamanho", "Logs: conferir o tamanho máximo"),
            Map.entry("ExecucaoMonitores.tick", "Monitoramento: verificações"),
            Map.entry("LimpezaBatidasJob.limpar", "Monitoramento: apagar dias vencidos"),
            Map.entry("LimpezaRegistrosJob.limpar", "Sistema: limpar registros antigos"),
            Map.entry("MonitorPushJob.push", "Centro de Operações: atualizar telas"),
            Map.entry("CapacidadeJob.medir", "Infraestrutura: medir a capacidade"),
            Map.entry("RotinaRegistro.gravar", "Infraestrutura: gravar estas execuções"),
            Map.entry("AdSincronizacaoJob.executar", "AD: sincronizar contas"),
            Map.entry("IntegracaoJob.processar", "Integrações: tratar avisos recebidos"),
            Map.entry("IntegracaoJob.enviar", "Integrações: enviar webhooks de saída"),
            Map.entry("FontesSimuladas.tick", "Centro de Operações: dados de demonstração"));

    /** Erro registrado no log enquanto a rotina desta thread roda: muitas tratam a própria falha e não lançam exceção. */
    private final ThreadLocal<String[]> erroDaThread = new ThreadLocal<>();
    private final Map<String, Estado> pendentes = new ConcurrentHashMap<>();

    @Resource
    private JdbcTemplate jdbcTemplate;

    void iniciar() {
        erroDaThread.set(new String[1]);
    }

    /** Chamado pelo log: só tem efeito dentro de uma rotina. */
    void anotarErro(String mensagem) {
        String[] atual = erroDaThread.get();
        if (atual != null && atual[0] == null) {
            atual[0] = limitar(mensagem);
        }
    }

    void concluir(String nome, long inicioNanos, Throwable lancada) {
        long duracao = (System.nanoTime() - inicioNanos) / 1_000_000;
        String[] anotado = erroDaThread.get();
        erroDaThread.remove();
        String erro = lancada != null ? limitar(lancada.getClass().getSimpleName() + ": " + lancada.getMessage())
                : anotado == null ? null : anotado[0];
        LocalDateTime agora = LocalDateTime.now();
        pendentes.compute(nome, (k, e) -> {
            Estado n = e == null ? new Estado() : e;
            n.ultima = agora;
            n.duracaoMs = duracao;
            n.erro = erro;
            n.execucoes++;
            if (erro != null) {
                n.falhas++;
                n.ultimaFalha = agora;
            }
            return n;
        });
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void gravar() {
        for (String nome : List.copyOf(pendentes.keySet())) {
            Estado e = pendentes.remove(nome);
            if (e == null) {
                continue;
            }
            try {
                // com mais de uma instância, os contadores somam e a última execução prevalece
                jdbcTemplate.update("""
                        INSERT INTO infra_rotina AS r (nome, ultima_execucao, duracao_ms, status, ultimo_erro, execucoes, falhas, ultima_falha)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (nome) DO UPDATE SET ultima_execucao = EXCLUDED.ultima_execucao, duracao_ms = EXCLUDED.duracao_ms,
                            status = EXCLUDED.status, ultimo_erro = coalesce(EXCLUDED.ultimo_erro, r.ultimo_erro),
                            execucoes = r.execucoes + EXCLUDED.execucoes, falhas = r.falhas + EXCLUDED.falhas,
                            ultima_falha = coalesce(EXCLUDED.ultima_falha, r.ultima_falha)""",
                        nome, Timestamp.valueOf(e.ultima), e.duracaoMs, e.erro == null ? "ok" : "erro", e.erro, e.execucoes, e.falhas,
                        e.ultimaFalha == null ? null : Timestamp.valueOf(e.ultimaFalha));
            } catch (RuntimeException ex) {
                log.warn("[gravar][{}: {}]", nome, ex.getMessage());
            }
        }
    }

    public List<InfraVOs.Rotina> listar() {
        return jdbcTemplate.query("SELECT * FROM infra_rotina ORDER BY status, nome", (rs, i) -> {
            Timestamp ultima = rs.getTimestamp("ultima_execucao");
            Timestamp falha = rs.getTimestamp("ultima_falha");
            String nome = rs.getString("nome");
            return new InfraVOs.Rotina(nome, ROTULOS.getOrDefault(nome, nome), ultima == null ? null : ultima.toLocalDateTime(),
                    rs.getLong("duracao_ms"), rs.getString("status"), rs.getString("ultimo_erro"), rs.getLong("execucoes"),
                    rs.getLong("falhas"), falha == null ? null : falha.toLocalDateTime());
        });
    }

    private static String limitar(String s) {
        return s == null ? "Falha sem mensagem" : s.length() > MAX_ERRO ? s.substring(0, MAX_ERRO) : s;
    }

    private static final class Estado {
        LocalDateTime ultima;
        long duracaoMs;
        String erro;
        long execucoes;
        long falhas;
        LocalDateTime ultimaFalha;
    }

}
