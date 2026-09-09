package com.processVisualisation.virtualKitchen.restclient.exception;

/**
 * Thrown when an AI provider client cannot successfully complete a request
 * for reasons other than authentication, timeout, or invalid response
 * content — for example, missing/invalid configuration, an invalid request,
 * a non-2xx HTTP error from the provider, or an invalid endpoint URI.
 */
public class AICommunicationException extends AIClientException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AICommunicationException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AICommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
