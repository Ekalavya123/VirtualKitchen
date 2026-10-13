package com.processVisualisation.virtualKitchen.ai.provider.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request to an AI provider times out or the provider cannot
 * be reached, typically wrapping a connectivity failure raised by the
 * underlying HTTP client.
 */
public class AiTimeoutException extends AiClientException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AiTimeoutException(String message) {
        super(message, null, HttpStatus.GATEWAY_TIMEOUT, "AI Timeout");
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause, HttpStatus.GATEWAY_TIMEOUT, "AI Timeout");
    }
}
