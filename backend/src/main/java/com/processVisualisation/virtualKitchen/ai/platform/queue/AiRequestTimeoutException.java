package com.processVisualisation.virtualKitchen.ai.platform.queue;

/**
 * Thrown when a queued AI job never finished within its configured
 * job-level timeout (distinct from a provider-level {@code
 * AiTimeoutException}, which is retried before this is ever reached).
 * Handled by {@code GlobalExceptionHandler} as HTTP 504.
 */
public class AiRequestTimeoutException extends RuntimeException {

    public AiRequestTimeoutException(String jobId) {
        super("AI request " + jobId + " did not complete within its configured timeout");
    }
}
