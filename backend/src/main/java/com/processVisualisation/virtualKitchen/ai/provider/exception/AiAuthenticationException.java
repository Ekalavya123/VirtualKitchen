package com.processVisualisation.virtualKitchen.ai.provider.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an AI provider rejects a request due to invalid or missing
 * credentials, typically in response to an HTTP 401 or 403 status returned
 * by the provider's API.
 */
public class AiAuthenticationException extends AiClientException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AiAuthenticationException(String message) {
        super(message, null, HttpStatus.UNAUTHORIZED, "AI Authentication Failed");
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AiAuthenticationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNAUTHORIZED, "AI Authentication Failed");
    }
}
