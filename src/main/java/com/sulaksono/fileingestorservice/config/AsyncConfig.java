package com.sulaksono.fileingestorservice.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures a dedicated {@link AsyncTaskExecutor} for methods annotated with {@code @Async}.
 * Spring Boot 3.5 deprecates {@code TaskExecutorBuilder},
 * {@link ThreadPoolTaskExecutor} initialized manually using the externalised TaskExecutionProperties.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private ThreadPoolTaskExecutor executor;

    @Bean("asyncExecutor")
    public AsyncTaskExecutor asyncExecutor(TaskExecutionProperties props) {
        executor = new ThreadPoolTaskExecutor();

        // Apply pool sizing coming from spring.task.execution.* (see application.yaml)
        var pool = props.getPool();
        executor.setCorePoolSize(pool.getCoreSize());
        executor.setMaxPoolSize(pool.getMaxSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setThreadNamePrefix(props.getThreadNamePrefix());
        executor.setTaskDecorator(new MDCTaskDecorator());

        executor.initialize();
        return executor;
    }

    @PreDestroy
    void gracefulShutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}