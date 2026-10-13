package com.processVisualisation.virtualKitchen.recipe.validation;

import com.processVisualisation.virtualKitchen.common.web.ApiException;
import com.processVisualisation.virtualKitchen.common.web.GlobalExceptionHandler;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a {@code Process} fails structural validation (e.g. an invalid
 * node/edge reference, a circular or self-referencing PROCESS node, a
 * duplicate MAIN process, or ownership that doesn't resolve to an existing
 * recipe). Handled by {@link GlobalExceptionHandler}, which returns an HTTP
 * 422 Unprocessable Entity response.
 */
public class ProcessValidationException extends ApiException {

    /**
     * Creates the exception with a descriptive message, typically the
     * joined list of individual validation errors.
     *
     * @param message description of why validation failed
     */
    public ProcessValidationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "Process Validation Failed", Outcome.REJECTED);
    }
}
