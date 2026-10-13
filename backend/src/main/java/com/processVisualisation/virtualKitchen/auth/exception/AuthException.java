package com.processVisualisation.virtualKitchen.auth.exception;

import com.processVisualisation.virtualKitchen.common.web.ApiException;
import com.processVisualisation.virtualKitchen.common.web.GlobalExceptionHandler;

import org.springframework.http.HttpStatus;

/**
 * Thrown by authentication/authorization flows (e.g. login, OTP verification,
 * token validation) when an auth-related operation fails, carrying the exact
 * {@link HttpStatus} the response should use. Handled by
 * {@link GlobalExceptionHandler}, which reads {@link #getStatus()} to build
 * the response status and reason phrase rather than using a fixed status.
 */
public class AuthException extends ApiException {

    /**
     * Creates the exception with a descriptive message and the HTTP status
     * the caller should respond with.
     *
     * @param message description of the auth failure
     * @param status the HTTP status to return for this failure
     */
    public AuthException(String message, HttpStatus status) {
        super(message, status, null, Outcome.REJECTED);
    }

    /**
     * Returns the HTTP status associated with this failure.
     *
     * @return the {@link HttpStatus} to use when responding to the client
     */
}
