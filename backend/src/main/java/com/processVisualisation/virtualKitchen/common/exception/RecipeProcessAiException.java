package com.processVisualisation.virtualKitchen.common.exception;

/**
 * Thrown when generating a recipe's process-visualization flow fails (e.g.
 * the flow could not be built or validated from the underlying recipe
 * template/step data). Handled by {@link GlobalExceptionHandler}, which
 * translates it into an HTTP 422 Unprocessable Entity response.
 */
public class RecipeProcessAiException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message and no underlying cause.
     *
     * @param message description of why flow generation failed
     */
    public RecipeProcessAiException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a descriptive message and the underlying
     * exception that caused the failure.
     *
     * @param message description of why flow generation failed
     * @param cause the underlying exception that triggered this failure
     */
    public RecipeProcessAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
