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

    /**
     * Creates a pool backed by a fixed-size {@link ExecutorService}, whose
     * threads are named after {@code poolName} and marked as daemon threads
     * so they never prevent JVM shutdown.
     *
     * @param nThreads number of worker threads to run tasks on; must be >= 1
     * @param poolName prefix used to name worker threads (e.g. {@code "poolName-pool-1"}),
     *                 useful for identifying threads in logs and thread dumps
     * @throws IllegalArgumentException if {@code nThreads} is less than 1
     */
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

    /**
     * {@inheritDoc}
     * The task body runs asynchronously on this pool's executor; any
     * exception it throws is caught by {@link #runSafely} and converted into
     * a {@link TaskResult#failure} rather than failing the returned future.
     */
    @Override
    public <R> CompletableFuture<TaskResult<R>> submit(NamedTask<R> task) {
        return CompletableFuture.supplyAsync(() -> runSafely(task), executor);
    }

    /**
     * {@inheritDoc}
     * Every task is submitted to the executor up front; {@code onTaskComplete}
     * runs on whichever worker thread finishes a given task, so it may be
     * invoked concurrently and out of the order {@code tasks} were passed in.
     * This method blocks the calling thread (via {@link CompletableFuture#join()})
     * until every submitted task has completed before assembling and
     * returning the aggregated {@link BatchResult}.
     */
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

    /**
     * Executes {@code task} and converts any thrown {@link Throwable} into a
     * failed {@link TaskResult} instead of letting it escape onto the worker
     * thread; the failure is also logged at WARN level.
     *
     * @param <R> the type of result produced by the task
     * @param task the task to run
     * @return a successful result if the task completed normally, otherwise
     *         a failed result wrapping the thrown exception
     */
    private <R> TaskResult<R> runSafely(NamedTask<R> task) {
        try {
            return TaskResult.success(task.taskId(), task.task().execute());
        } catch (Throwable t) {
            log.warn("Task {} failed", task.taskId(), t);
            return TaskResult.failure(task.taskId(), t);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int nThreads() {
        return nThreads;
    }

    /**
     * {@inheritDoc}
     * Delegates to {@link ExecutorService#shutdown()}: previously submitted
     * tasks continue running to completion, but the executor stops accepting
     * new tasks.
     */
    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
