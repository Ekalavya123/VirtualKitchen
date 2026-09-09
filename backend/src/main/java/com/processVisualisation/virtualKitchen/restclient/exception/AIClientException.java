package com.processVisualisation.virtualKitchen.restclient.exception;

/**
 * Base unchecked exception for all failures raised by the AI provider and
 * storage clients in this package. Provider-specific failure conditions
 * (communication errors, authentication failures, timeouts, invalid
 * responses) are represented by the dedicated subclasses in this package
 * rather than being thrown directly as this type.
 */
public class AIClientException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AIClientException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AIClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
