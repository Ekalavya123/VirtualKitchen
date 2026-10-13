package com.processVisualisation.virtualKitchen.ai.recipe;

import com.processVisualisation.virtualKitchen.common.web.ApiException;
import com.processVisualisation.virtualKitchen.common.web.GlobalExceptionHandler;

import org.springframework.http.HttpStatus;

/**
 * Thrown when generating a recipe's process-visualization flow fails (e.g.
 * the flow could not be built or validated from the underlying recipe
 * template/step data). Handled by {@link GlobalExceptionHandler}, which
 * translates it into an HTTP 422 Unprocessable Entity response.
 */
public class RecipeProcessAiException extends ApiException {

    /**
     * Creates the exception with a descriptive message and no underlying cause.
     *
     * @param message description of why flow generation failed
     */
    public RecipeProcessAiException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "Recipe Flow Generation Failed", Outcome.REJECTED);
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * exception that caused the failure.
     *
     * @param message description of why flow generation failed
     * @param cause the underlying exception that triggered this failure
     */
    public RecipeProcessAiException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNPROCESSABLE_ENTITY, "Recipe Flow Generation Failed", Outcome.REJECTED);
    }
}
