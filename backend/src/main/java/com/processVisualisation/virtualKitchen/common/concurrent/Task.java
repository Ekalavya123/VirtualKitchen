package com.processVisualisation.virtualKitchen.common.concurrent;

/**
 * A single unit of work that can be submitted to a {@link TaskPool}.
 * Framework-agnostic on purpose so any feature can reuse the pool without
 * depending on Spring or on the visualization pipeline it was built for.
 */
@FunctionalInterface
public interface Task<R> {
    R execute() throws Exception;
}
