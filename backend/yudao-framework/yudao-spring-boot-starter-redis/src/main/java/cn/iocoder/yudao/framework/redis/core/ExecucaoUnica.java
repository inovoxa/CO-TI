package cn.iocoder.yudao.framework.redis.core;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.lang.management.ManagementFactory;
import java.time.Duration;

/**
 * CO-TI: com vários backends atrás do balanceador, cada tarefa agendada roda em uma instância só a cada vez. A
 * primeira instância que chega marca a vez no Redis (com validade) e roda; as outras veem a marca e pulam. A marca não
 * é apagada no fim, senão uma instância cujo relógio disparou segundos depois rodaria a mesma vez de novo.
 *
 * <p>Se o Redis estiver fora do ar a tarefa roda assim mesmo: as tarefas que usam isto toleram rodar em dobro, mas não
 * podem deixar de rodar.
 */
@Slf4j
public class ExecucaoUnica {

    private static final String PREFIXO = "coti:execucao-unica:";
    private static final String INSTANCIA = ManagementFactory.getRuntimeMXBean().getName();

    private final RedissonClient redissonClient;

    public ExecucaoUnica(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * @param nome identifica a tarefa (o mesmo em todas as instâncias)
     * @param vez  quanto tempo esta execução vale: um pouco menos que o intervalo da tarefa
     * @return true se rodou aqui, false se outra instância já fez esta vez
     */
    public boolean executar(String nome, Duration vez, Runnable tarefa) {
        boolean minha;
        try {
            RBucket<String> marca = redissonClient.getBucket(PREFIXO + nome, StringCodec.INSTANCE);
            minha = marca.setIfAbsent(INSTANCIA, vez);
        } catch (RuntimeException e) {
            log.warn("[executar][{}: Redis indisponível, rodando sem coordenar: {}]", nome, e.getMessage());
            minha = true;
        }
        if (!minha) {
            log.debug("[executar][{}: outra instância já fez esta vez]", nome);
            return false;
        }
        tarefa.run();
        return true;
    }

}
