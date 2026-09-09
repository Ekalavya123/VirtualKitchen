package com.processVisualisation.virtualKitchen.common.concurrent;

import java.util.List;

/**
 * Aggregated outcome of a {@link TaskPool#submitAll} call — the "reduce input"
 * the caller inspects once every submitted task has finished.
 */
public record BatchResult<R>(List<TaskResult<R>> results) {

    public List<TaskResult<R>> successes() {
        return results.stream().filter(TaskResult::isSuccess).toList();
    }

    public List<TaskResult<R>> failures() {
        return results.stream().filter(r -> !r.isSuccess()).toList();
    }

    public boolean allSucceeded() {
        return failures().isEmpty();
    }
}
