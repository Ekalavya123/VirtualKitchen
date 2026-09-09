package com.processVisualisation.virtualKitchen.restclient.exception;

/**
 * Thrown when a request to an AI provider times out or the provider cannot
 * be reached, typically wrapping a connectivity failure raised by the
 * underlying HTTP client.
 */
public class AITimeoutException extends AIClientException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AITimeoutException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AITimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
