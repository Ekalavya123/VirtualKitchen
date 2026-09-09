package com.processVisualisation.virtualKitchen.common.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * {@link TaskPool} backed by a fixed-size {@link ExecutorService}. Every task
 * body is wrapped so any exception is captured as a {@link TaskResult#failure}
 * rather than being allowed to escape the worker thread.
 */
public class ThreadPoolTaskPool implements TaskPool {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolTaskPool.class);

    private final int nThreads;
    private final ExecutorService executor;

    public ThreadPoolTaskPool(int nThreads, String poolName) {
        if (nThreads < 1) {
            throw new IllegalArgumentException("nThreads must be >= 1, got " + nThreads);
        }
        this.nThreads = nThreads;
        this.executor = Executors.newFixedThreadPool(nThreads, namedThreadFactory(poolName));
    }

    private static ThreadFactory namedThreadFactory(String poolName) {
        AtomicLong counter = new AtomicLong(0);
        return runnable -> {
            Thread thread = new Thread(runnable, poolName + "-pool-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public <R> CompletableFuture<TaskResult<R>> submit(NamedTask<R> task) {
        return CompletableFuture.supplyAsync(() -> runSafely(task), executor);
    }

    @Override
    public <R> BatchResult<R> submitAll(List<NamedTask<R>> tasks, Consumer<TaskResult<R>> onTaskComplete) {
        List<CompletableFuture<TaskResult<R>>> futures = new ArrayList<>(tasks.size());
        for (NamedTask<R> task : tasks) {
            CompletableFuture<TaskResult<R>> future = CompletableFuture
                    .supplyAsync(() -> runSafely(task), executor)
                    .thenApply(result -> {
                        if (onTaskComplete != null) {
                            onTaskComplete.accept(result);
                        }
                        return result;
                    });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<TaskResult<R>> results = futures.stream().map(CompletableFuture::join).toList();
        return new BatchResult<>(results);
    }

    private <R> TaskResult<R> runSafely(NamedTask<R> task) {
        try {
            return TaskResult.success(task.taskId(), task.task().execute());
        } catch (Throwable t) {
            log.warn("Task {} failed", task.taskId(), t);
            return TaskResult.failure(task.taskId(), t);
        }
    }

    @Override
    public int nThreads() {
        return nThreads;
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
