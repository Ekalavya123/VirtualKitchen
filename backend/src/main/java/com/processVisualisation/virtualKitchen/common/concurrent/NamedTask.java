package com.processVisualisation.virtualKitchen.common.concurrent;

/**
 * Pairs a caller-chosen id with a {@link Task} so the corresponding
 * {@link TaskResult} can be correlated back to whatever domain object it
 * represents (e.g. a recipe step id), regardless of completion order.
 *
 * @param <R> the type of result produced by the wrapped task
 * @param taskId caller-chosen identifier used to correlate this task's result
 *               back to its originating domain object
 * @param task the unit of work to execute
 */
public record NamedTask<R>(String taskId, Task<R> task) {
}
