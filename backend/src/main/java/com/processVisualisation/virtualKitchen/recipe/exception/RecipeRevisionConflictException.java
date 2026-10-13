package com.processVisualisation.virtualKitchen.recipe.exception;

import com.processVisualisation.virtualKitchen.common.web.ApiException;
import com.processVisualisation.virtualKitchen.common.web.GlobalExceptionHandler;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a recipe-level process save was based on a revision of the recipe that is no longer
 * current — i.e. the recipe was saved from another tab/device since the client last loaded or
 * saved it. Handled by {@link GlobalExceptionHandler}, which returns an HTTP 409 Conflict response
 * so the client can let the user choose between reloading and overwriting.
 */
public class RecipeRevisionConflictException extends ApiException {
    /**
     * Creates the exception with a descriptive message.
     *
     * @param message description of the conflicting revision
     */
    public RecipeRevisionConflictException(String message) {
        super(message, HttpStatus.CONFLICT, null, Outcome.REJECTED);
    }
}
