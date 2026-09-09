package com.processVisualisation.virtualKitchen.common.concurrent;

/**
 * A single unit of work that can be submitted to a {@link TaskPool}.
 * Framework-agnostic on purpose so any feature can reuse the pool without
 * depending on Spring or on the visualization pipeline it was built for.
 *
 * @param <R> the type of result produced by this task
 */
@FunctionalInterface
public interface Task<R> {
    /**
     * Runs this unit of work and produces its result.
     *
     * @return the result of executing this task
     * @throws Exception if the work fails; a {@link TaskPool} executing this
     *         task is expected to catch the failure and capture it as a
     *         {@link TaskResult#failure} rather than let it propagate
     */
    R execute() throws Exception;
}
