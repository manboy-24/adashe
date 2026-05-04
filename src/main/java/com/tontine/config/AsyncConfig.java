package com.tontine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Copie le MDC du thread HTTP vers le thread async.
     * Permet au correlationId d'apparaître dans les logs des méthodes @Async.
     */
    private static final TaskDecorator MDC_DECORATOR = runnable -> {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (ctx != null) MDC.setContextMap(ctx);
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    };

    @Bean(name = "notifExecutor")
    public Executor notifExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(10);
        exec.setMaxPoolSize(50);
        exec.setQueueCapacity(1000);
        exec.setThreadNamePrefix("notif-async-");
        exec.setTaskDecorator(MDC_DECORATOR);
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(15);
        // Log et abandonne si la file est saturée (évite de bloquer le thread appelant)
        exec.setRejectedExecutionHandler(
            (r, pool) -> log.error("[notifExecutor] File saturée ({} tâches) — notification abandonnée", pool.getQueue().size())
        );
        exec.initialize();
        return exec;
    }

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(5);
        exec.setQueueCapacity(1000);
        exec.setThreadNamePrefix("audit-async-");
        exec.setTaskDecorator(MDC_DECORATOR);
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        // Log l'abandon d'un audit — ne jamais bloquer silencieusement
        exec.setRejectedExecutionHandler(
            (r, pool) -> log.error("[auditExecutor] File saturée ({} tâches) — entrée d'audit PERDUE, investiguer immédiatement", pool.getQueue().size())
        );
        exec.initialize();
        return exec;
    }
}
