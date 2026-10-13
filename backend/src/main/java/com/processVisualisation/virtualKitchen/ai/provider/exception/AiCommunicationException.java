package com.processVisualisation.virtualKitchen.ai.provider.exception;

/**
 * Thrown when an AI provider client cannot successfully complete a request
 * for reasons other than authentication, timeout, or invalid response
 * content — for example, missing/invalid configuration, an invalid request,
 * a non-2xx HTTP error from the provider, or an invalid endpoint URI.
 */
public class AiCommunicationException extends AiClientException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AiCommunicationException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AiCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
