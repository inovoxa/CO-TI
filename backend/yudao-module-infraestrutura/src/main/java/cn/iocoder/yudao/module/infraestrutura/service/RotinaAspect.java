package cn.iocoder.yudao.module.infraestrutura.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registra toda rotina {@code @Scheduled} dos módulos, sem precisar mexer em cada uma. A maioria trata a própria falha e só escreve
 * no log; por isso os erros registrados pela thread da rotina também contam como falha dela.
 */
@Aspect
@Component
public class RotinaAspect {

    @Resource
    private RotinaRegistro registro;

    private AppenderBase<ILoggingEvent> ouvinte;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) && within(cn.iocoder.yudao.module..*)")
    public Object registrar(ProceedingJoinPoint pjp) throws Throwable {
        String nome = pjp.getSignature().getDeclaringType().getSimpleName() + "." + pjp.getSignature().getName();
        long inicio = System.nanoTime();
        registro.iniciar();
        try {
            Object r = pjp.proceed();
            registro.concluir(nome, inicio, null);
            return r;
        } catch (Throwable t) {
            registro.concluir(nome, inicio, t);
            throw t;
        }
    }

    @PostConstruct
    void ouvirErrosDoLog() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        ouvinte = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent e) {
                if (e.getLevel().isGreaterOrEqual(Level.ERROR)) {
                    registro.anotarErro(e.getFormattedMessage());
                }
            }
        };
        ouvinte.setContext(contexto);
        ouvinte.setName("coti-rotinas");
        ouvinte.start();
        contexto.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(ouvinte);
    }

    @PreDestroy
    void pararDeOuvir() {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(ouvinte);
        ouvinte.stop();
    }

}
