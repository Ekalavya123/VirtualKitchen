package com.processVisualisation.virtualKitchen.common.concurrent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A bounded worker pool that runs {@link Task}s concurrently, limited to a
 * fixed number of threads ("nThreads"), and lets a caller either fire a single
 * task or run a batch and collect the aggregated result once every task in
 * the batch has finished (map-then-reduce-input).
 * <p>
 * Generic and feature-agnostic by design so other parts of the application
 * can reuse the same abstraction — see {@link TaskPoolFactory}.
 */
public interface TaskPool {

    /**
     * Submits a single task without blocking the caller.
     */
    <R> CompletableFuture<TaskResult<R>> submit(NamedTask<R> task);

    /**
     * Runs every task in {@code tasks} through this pool's bounded worker
     * threads, invoking {@code onTaskComplete} as each one finishes (useful
     * for incremental progress reporting), and blocks the calling thread
     * until all tasks have completed. The caller performs the actual reduce
     * step on the returned {@link BatchResult}.
     * <p>
     * A single failing task never aborts the batch or affects other tasks —
     * its outcome is captured as {@link TaskResult#failure}.
     */
    <R> BatchResult<R> submitAll(List<NamedTask<R>> tasks, Consumer<TaskResult<R>> onTaskComplete);

    int nThreads();

    void shutdown();
}
