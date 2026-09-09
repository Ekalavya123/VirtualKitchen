package com.processVisualisation.virtualKitchen.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown by authentication/authorization flows (e.g. login, OTP verification,
 * token validation) when an auth-related operation fails, carrying the exact
 * {@link HttpStatus} the response should use. Handled by
 * {@link GlobalExceptionHandler}, which reads {@link #getStatus()} to build
 * the response status and reason phrase rather than using a fixed status.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Creates the exception with a descriptive message and the HTTP status
     * the caller should respond with.
     *
     * @param message description of the auth failure
     * @param status the HTTP status to return for this failure
     */
    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status associated with this failure.
     *
     * @return the {@link HttpStatus} to use when responding to the client
     */
    public HttpStatus getStatus() {
        return status;
    }
}
