package com.processVisualisation.virtualKitchen.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an AI Recipe Creation request can't be honoured: an invalid task selection (400), or
 * an action the workflow's current state doesn't allow, such as approving before the generated
 * process reached the editor or retrying a task that hasn't failed (409). Handled by
 * {@link GlobalExceptionHandler}.
 */
public class RecipeAiWorkflowException extends RuntimeException {

    private final HttpStatus status;

    public RecipeAiWorkflowException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static RecipeAiWorkflowException badRequest(String message) {
        return new RecipeAiWorkflowException(HttpStatus.BAD_REQUEST, message);
    }

    public static RecipeAiWorkflowException conflict(String message) {
        return new RecipeAiWorkflowException(HttpStatus.CONFLICT, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
