package com.sulaksono.fileingestorservice.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MDCTaskDecoratorTest {

    private final MDCTaskDecorator decorator = new MDCTaskDecorator();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void decorate_shouldPropagateMdcToRunnable() {
        MDC.put("requestId", "abc-123");

        AtomicReference<String> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(
                () -> captured.set(MDC.get("requestId"))
        );

        decorated.run();

        assertThat(captured.get()).isEqualTo("abc-123");
    }

    @Test
    void decorate_shouldClearMdcAfterRunnableFinishes() {
        MDC.put("requestId", "abc-123");

        Runnable decorated = decorator.decorate(() -> {
            assertThat(MDC.get("requestId")).isEqualTo("abc-123");
        });

        decorated.run();

        assertThat(MDC.getCopyOfContextMap())
                .isNullOrEmpty();
    }

    @Test
    void decorate_shouldClearMdcEvenWhenRunnableThrows() {
        MDC.put("requestId", "abc-123");

        Runnable decorated = decorator.decorate(() -> {
            throw new RuntimeException("boom");
        });

        try {
            decorated.run();
        } catch (RuntimeException ignored) {
        }

        assertThat(MDC.getCopyOfContextMap())
                .isNullOrEmpty();
    }

    @Test
    void decorate_shouldSnapshotMdcAtDecoratingTimeNotAtRunTime() {
        MDC.put("requestId", "snapshot-value");

        Runnable decorated = decorator.decorate(
                () -> assertThat(MDC.get("requestId"))
                        .isEqualTo("snapshot-value")
        );

        MDC.put("requestId", "later-value");

        decorated.run();
    }

    @Test
    void decorate_whenNoMdcSet_shouldNotFail() {
        MDC.clear();

        AtomicReference<String> captured = new AtomicReference<>("initial");

        Runnable decorated = decorator.decorate(
                () -> captured.set(MDC.get("requestId"))
        );

        decorated.run();

        assertThat(captured.get()).isNull();
    }

    @Test
    void decorate_shouldPropagateMdcAcrossThreads() throws Exception {
        MDC.put("requestId", "cross-thread-id");

        AtomicReference<String> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(
                () -> captured.set(MDC.get("requestId"))
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            executor.submit(decorated).get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(captured.get()).isEqualTo("cross-thread-id");
    }

    @Test
    void decorate_multipleKeys_shouldAllBePropagated() {
        MDC.put("requestId", "req-1");
        MDC.put("userId", "user-42");

        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> userId = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            requestId.set(MDC.get("requestId"));
            userId.set(MDC.get("userId"));
        });

        decorated.run();

        assertThat(requestId.get()).isEqualTo("req-1");
        assertThat(userId.get()).isEqualTo("user-42");
    }
}