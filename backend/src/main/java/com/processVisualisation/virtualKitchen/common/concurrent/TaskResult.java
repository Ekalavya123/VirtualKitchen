package com.processVisualisation.virtualKitchen.common.concurrent;

/**
 * Outcome of one {@link NamedTask} execution. Exactly one of {@code value}/{@code error}
 * is populated.
 *
 * @param <R> the type of value produced by the task on success
 * @param taskId the identifier of the {@link NamedTask} this result belongs to
 * @param value the task's return value on success, or {@code null} on failure
 * @param error the exception thrown by the task on failure, or {@code null} on success
 */
public record TaskResult<R>(String taskId, R value, Throwable error) {

    /**
     * Indicates whether the task completed without error.
     *
     * @return {@code true} if {@code error} is {@code null}, {@code false} otherwise
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Creates a successful result.
     *
     * @param <R> the type of the produced value
     * @param taskId the identifier of the task that succeeded
     * @param value the value produced by the task
     * @return a {@link TaskResult} with {@code error} set to {@code null}
     */
    public static <R> TaskResult<R> success(String taskId, R value) {
        return new TaskResult<>(taskId, value, null);
    }

    /**
     * Creates a failed result.
     *
     * @param <R> the type of value the task would have produced
     * @param taskId the identifier of the task that failed
     * @param error the exception the task threw
     * @return a {@link TaskResult} with {@code value} set to {@code null}
     */
    public static <R> TaskResult<R> failure(String taskId, Throwable error) {
        return new TaskResult<>(taskId, null, error);
    }
}
