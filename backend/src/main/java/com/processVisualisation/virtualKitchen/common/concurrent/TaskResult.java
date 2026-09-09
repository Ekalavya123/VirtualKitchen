package com.processVisualisation.virtualKitchen.common.concurrent;

/**
 * Outcome of one {@link NamedTask} execution. Exactly one of {@code value}/{@code error}
 * is populated.
 */
public record TaskResult<R>(String taskId, R value, Throwable error) {

    public boolean isSuccess() {
        return error == null;
    }

    public static <R> TaskResult<R> success(String taskId, R value) {
        return new TaskResult<>(taskId, value, null);
    }

    public static <R> TaskResult<R> failure(String taskId, Throwable error) {
        return new TaskResult<>(taskId, null, error);
    }
}
