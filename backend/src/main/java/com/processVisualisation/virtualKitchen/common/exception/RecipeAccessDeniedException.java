package com.processVisualisation.virtualKitchen.common.exception;

/**
 * Thrown when a user attempts to view or act on a recipe/recipe resource
 * they are not authorized to access (e.g. a recipe owned by another user).
 * Handled by {@link GlobalExceptionHandler}, which returns an HTTP 403
 * Forbidden response.
 */
public class RecipeAccessDeniedException extends RuntimeException {
    /**
     * Creates the exception with a descriptive message.
     *
     * @param message description of the access-denied condition
     */
    public RecipeAccessDeniedException(String message) {
        super(message);
    }
}
