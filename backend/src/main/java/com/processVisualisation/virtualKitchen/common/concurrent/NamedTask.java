package com.processVisualisation.virtualKitchen.common.concurrent;

/**
 * Pairs a caller-chosen id with a {@link Task} so the corresponding
 * {@link TaskResult} can be correlated back to whatever domain object it
 * represents (e.g. a recipe step id), regardless of completion order.
 */
public record NamedTask<R>(String taskId, Task<R> task) {
}
