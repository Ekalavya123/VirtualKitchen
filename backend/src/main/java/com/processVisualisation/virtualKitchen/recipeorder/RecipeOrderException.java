package com.processVisualisation.virtualKitchen.recipeorder;

import org.springframework.http.HttpStatus;

/**
 * A recipe-order request that cannot be honoured: a missing or foreign order (404), invalid order
 * details (400), an action the order's current status does not allow or a stale client view (409),
 * or a recipe that cannot be ordered as it stands (422). Carries its own HTTP status, like
 * {@code AuthException}.
 */
public class RecipeOrderException extends RuntimeException {

    private final HttpStatus status;

    public RecipeOrderException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static RecipeOrderException notFound() {
        return new RecipeOrderException("Recipe order not found", HttpStatus.NOT_FOUND);
    }

    public static RecipeOrderException badRequest(String message) {
        return new RecipeOrderException(message, HttpStatus.BAD_REQUEST);
    }

    public static RecipeOrderException conflict(String message) {
        return new RecipeOrderException(message, HttpStatus.CONFLICT);
    }

    public static RecipeOrderException unprocessable(String message) {
        return new RecipeOrderException(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
