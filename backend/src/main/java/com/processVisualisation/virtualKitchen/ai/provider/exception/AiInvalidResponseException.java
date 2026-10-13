package com.processVisualisation.virtualKitchen.ai.provider.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an AI provider returns a response that cannot be used —
 * for example, an empty response, a response with no usable message or
 * image content, or a body that cannot be parsed as the expected JSON.
 */
public class AiInvalidResponseException extends AiClientException {

    /**
     * Creates the exception with a descriptive message and no cause.
     *
     * @param message a message describing the failure
     */
    public AiInvalidResponseException(String message) {
        super(message, null, HttpStatus.BAD_GATEWAY, "AI Invalid Response");
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * cause.
     *
     * @param message a message describing the failure
     * @param cause the underlying exception that caused this failure
     */
    public AiInvalidResponseException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY, "AI Invalid Response");
    }
}
