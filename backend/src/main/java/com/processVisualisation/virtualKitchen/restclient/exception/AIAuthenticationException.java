package com.processVisualisation.virtualKitchen.restclient.exception;

/**
 * Thrown when an AI provider rejects a request due to invalid or missing
 * credentials, typically in response to an HTTP 401 or 403 status returned
 * by the provider's API.
 */
public class AIAuthenticationException extends AIClientException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AIAuthenticationException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AIAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
