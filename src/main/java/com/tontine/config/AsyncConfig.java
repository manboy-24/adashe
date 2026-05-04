package com.tontine.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

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
        exec.setCorePoolSize(5);
        exec.setMaxPoolSize(20);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("notif-async-");
        exec.setTaskDecorator(MDC_DECORATOR);
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(15);
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
        exec.initialize();
        return exec;
    }
}
