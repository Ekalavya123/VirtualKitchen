package com.processVisualisation.virtualKitchen.ai.provider.exception;

import java.time.Duration;

/**
 * Thrown when a provider throttles a request (HTTP 429 per-minute rate limit, or 503 "model
 * overloaded") and it is worth trying again after a pause. The queue retries it with its own,
 * longer backoff than an ordinary communication failure; a quota that cannot recover soon (a daily
 * limit) must be reported as a plain {@link AiCommunicationException} instead.
 */
public class AiRateLimitedException extends AiCommunicationException {

    private final Duration retryAfter;

    /**
     * @param retryAfter how long the provider asked us to wait, or null when it did not say
     */
    public AiRateLimitedException(String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    /** The provider's requested wait (Retry-After / RetryInfo), or null when it gave none. */
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
