package com.processVisualisation.virtualKitchen.ai.platform.queue;

import com.processVisualisation.virtualKitchen.common.web.ApiException;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a queued AI job never finished within its configured
 * job-level timeout (distinct from a provider-level {@code
 * AiTimeoutException}, which is retried before this is ever reached).
 * Handled by {@code GlobalExceptionHandler} as HTTP 504.
 */
public class AiRequestTimeoutException extends ApiException {

    public AiRequestTimeoutException(String jobId) {
        super("AI request " + jobId + " did not complete within its configured timeout",
                HttpStatus.GATEWAY_TIMEOUT, "AI Request Timeout", Outcome.UPSTREAM_FAILURE);
    }
}
