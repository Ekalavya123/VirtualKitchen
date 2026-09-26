package com.processVisualisation.virtualKitchen.common.exception;

/**
 * Thrown when a {@code Process} fails structural validation (e.g. an invalid
 * node/edge reference, a circular or self-referencing PROCESS node, a
 * duplicate MAIN process, or ownership that doesn't resolve to an existing
 * recipe). Handled by {@link GlobalExceptionHandler}, which returns an HTTP
 * 422 Unprocessable Entity response.
 */
public class ProcessValidationException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message, typically the
     * joined list of individual validation errors.
     *
     * @param message description of why validation failed
     */
    public ProcessValidationException(String message) {
        super(message);
    }
}
