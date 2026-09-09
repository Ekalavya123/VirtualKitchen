package com.processVisualisation.virtualKitchen.common.exception;

/**
 * Thrown when a lookup for a user (e.g. by id or email) finds no matching
 * record. Handled by {@link GlobalExceptionHandler}, which returns an HTTP
 * 400 Bad Request response.
 */
public class UserNotFoundException extends RuntimeException {
    /**
     * Creates the exception with a descriptive message.
     *
     * @param message description of which user lookup failed
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}