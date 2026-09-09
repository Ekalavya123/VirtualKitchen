package com.processVisualisation.virtualKitchen.restclient.exception;

/**
 * Thrown when an AI provider returns a response that cannot be used —
 * for example, an empty response, a response with no usable message or
 * image content, or a body that cannot be parsed as the expected JSON.
 */
public class AIInvalidResponseException extends AIClientException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AIInvalidResponseException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AIInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
