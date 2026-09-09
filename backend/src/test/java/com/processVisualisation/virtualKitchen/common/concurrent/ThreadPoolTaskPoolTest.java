package com.processVisualisation.virtualKitchen.common.concurrent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadPoolTaskPoolTest {

    @Test
    void submitAll_boundsConcurrencyToNThreads() {
        int nThreads = 2;
        ThreadPoolTaskPool pool = new ThreadPoolTaskPool(nThreads, "bound-test");
        AtomicInteger running = new AtomicInteger(0);
        AtomicInteger maxRunning = new AtomicInteger(0);

        List<NamedTask<Void>> tasks = List.of(
                task("t1", running, maxRunning),
                task("t2", running, maxRunning),
                task("t3", running, maxRunning),
                task("t4", running, maxRunning),
                task("t5", running, maxRunning),
                task("t6", running, maxRunning)
        );

        BatchResult<Void> result = pool.submitAll(tasks, r -> {
        });

        assertTrue(result.allSucceeded());
        assertTrue(maxRunning.get() <= nThreads, "observed max concurrent = " + maxRunning.get());
        pool.shutdown();
    }

    private NamedTask<Void> task(String id, AtomicInteger running, AtomicInteger maxRunning) {
        return new NamedTask<>(id, () -> {
            int current = running.incrementAndGet();
            maxRunning.updateAndGet(prev -> Math.max(prev, current));
            try {
                Thread.sleep(50);
            } finally {
                running.decrementAndGet();
            }
            return null;
        });
    }

    @Test
    void submitAll_aggregatesResultsForEveryTaskRegardlessOfCompletionOrder() {
        ThreadPoolTaskPool pool = new ThreadPoolTaskPool(4, "order-test");

        List<NamedTask<Integer>> tasks = List.of(
                new NamedTask<>("slow", sleepingTask(80, 1)),
                new NamedTask<>("fast", sleepingTask(5, 2)),
                new NamedTask<>("medium", sleepingTask(30, 3))
        );

        BatchResult<Integer> result = pool.submitAll(tasks, r -> {
        });

        assertEquals(3, result.results().size());
        assertTrue(result.allSucceeded());
        assertEquals(1, valueFor(result, "slow"));
        assertEquals(2, valueFor(result, "fast"));
        assertEquals(3, valueFor(result, "medium"));
        pool.shutdown();
    }

    private Task<Integer> sleepingTask(long millis, int value) {
        return () -> {
            Thread.sleep(millis);
            return value;
        };
    }

    private Integer valueFor(BatchResult<Integer> result, String taskId) {
        return result.results().stream()
                .filter(r -> r.taskId().equals(taskId))
                .findFirst()
                .orElseThrow()
                .value();
    }

    @Test
    void submitAll_isolatesFailuresAndKeepsPoolUsable() {
        ThreadPoolTaskPool pool = new ThreadPoolTaskPool(2, "failure-test");

        List<NamedTask<String>> tasks = List.of(
                new NamedTask<>("ok-1", () -> "value-1"),
                new NamedTask<>("boom", () -> {
                    throw new IllegalStateException("boom");
                }),
                new NamedTask<>("ok-2", () -> "value-2")
        );

        BatchResult<String> result = pool.submitAll(tasks, r -> {
        });

        assertEquals(2, result.successes().size());
        assertEquals(1, result.failures().size());
        assertEquals("boom", result.failures().get(0).taskId());
        assertTrue(result.failures().get(0).error() instanceof IllegalStateException);
        assertTrue(!result.allSucceeded());

        // pool must still be usable after a failing task
        BatchResult<String> followUp = pool.submitAll(
                List.of(new NamedTask<>("after", () -> "still-works")), r -> {
                });
        assertEquals("still-works", followUp.results().get(0).value());
        pool.shutdown();
    }

    @Test
    void submitAll_invokesOnTaskCompleteExactlyOncePerTask() {
        ThreadPoolTaskPool pool = new ThreadPoolTaskPool(4, "callback-test");
        AtomicInteger callbackCount = new AtomicInteger(0);

        List<NamedTask<Void>> tasks = List.of(
                new NamedTask<>("a", () -> null),
                new NamedTask<>("b", () -> null),
                new NamedTask<>("c", () -> null),
                new NamedTask<>("d", () -> null)
        );

        pool.submitAll(tasks, r -> callbackCount.incrementAndGet());

        assertEquals(tasks.size(), callbackCount.get());
        pool.shutdown();
    }

    @Test
    void submit_runsAsingleTaskAndCompletesTheFuture() throws Exception {
        ThreadPoolTaskPool pool = new ThreadPoolTaskPool(1, "single-test");

        CompletableFuture<TaskResult<String>> future = pool.submit(new NamedTask<>("only", () -> "done"));
        TaskResult<String> result = future.get(2, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("done", result.value());
        pool.shutdown();
    }
}
