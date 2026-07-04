package com.sulaksono.fileingestorservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AsyncConfig.class);

    @Test
    void asyncExecutorBean_shouldBeRegistered() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("asyncExecutor");

            assertThat(context.getBean("asyncExecutor"))
                    .isInstanceOf(AsyncTaskExecutor.class)
                    .isInstanceOf(ThreadPoolTaskExecutor.class);
        });
    }

    @Test
    void asyncExecutor_shouldApplyPoolPropertiesFromConfig() {
        contextRunner
                .withPropertyValues(
                        "spring.task.execution.pool.core-size=4",
                        "spring.task.execution.pool.max-size=16",
                        "spring.task.execution.pool.queue-capacity=200",
                        "spring.task.execution.thread-name-prefix=custom-async-"
                )
                .run(context -> {
                    ThreadPoolTaskExecutor executor =
                            context.getBean("asyncExecutor", ThreadPoolTaskExecutor.class);

                    assertThat(executor.getCorePoolSize()).isEqualTo(4);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(16);
                    assertThat(executor.getQueueCapacity()).isEqualTo(200);
                    assertThat(executor.getThreadNamePrefix()).isEqualTo("custom-async-");
                });
    }

    @Test
    void asyncExecutor_shouldUseMdcTaskDecorator() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor =
                    context.getBean("asyncExecutor", ThreadPoolTaskExecutor.class);

            assertThat(executor)
                    .extracting("taskDecorator")
                    .isInstanceOf(MDCTaskDecorator.class);
        });
    }

    @Test
    void asyncExecutor_shouldRunTaskSuccessfully() {
        contextRunner.run(context -> {
            AsyncTaskExecutor executor =
                    context.getBean("asyncExecutor", AsyncTaskExecutor.class);

            var future = executor.submit(() -> "done");

            assertThat(future.get()).isEqualTo("done");
        });
    }
}