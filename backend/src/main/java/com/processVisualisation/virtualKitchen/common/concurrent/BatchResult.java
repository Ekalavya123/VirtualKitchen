package com.processVisualisation.virtualKitchen.common.concurrent;

import java.util.List;

/**
 * Aggregated outcome of a {@link TaskPool#submitAll} call — the "reduce input"
 * the caller inspects once every submitted task has finished.
 *
 * @param <R> the type of value produced by each successful task
 * @param results the per-task results, in the same order the tasks were submitted
 */
public record BatchResult<R>(List<TaskResult<R>> results) {

    /**
     * Filters the batch down to the tasks that completed successfully.
     *
     * @return the successful {@link TaskResult}s, preserving their order in {@link #results()}
     */
    public List<TaskResult<R>> successes() {
        return results.stream().filter(TaskResult::isSuccess).toList();
    }

    /**
     * Filters the batch down to the tasks that failed.
     *
     * @return the failed {@link TaskResult}s, preserving their order in {@link #results()}
     */
    public List<TaskResult<R>> failures() {
        return results.stream().filter(r -> !r.isSuccess()).toList();
    }

    /**
     * Indicates whether every task in the batch succeeded.
     *
     * @return {@code true} if {@link #failures()} is empty, {@code false} otherwise
     */
    public boolean allSucceeded() {
        return failures().isEmpty();
    }
}
