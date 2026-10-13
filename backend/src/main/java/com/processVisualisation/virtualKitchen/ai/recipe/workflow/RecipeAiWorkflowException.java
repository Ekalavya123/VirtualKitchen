package com.processVisualisation.virtualKitchen.ai.recipe.workflow;

import com.processVisualisation.virtualKitchen.common.web.ApiException;
import com.processVisualisation.virtualKitchen.common.web.GlobalExceptionHandler;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an AI Recipe Creation request can't be honoured: an invalid task selection (400), or
 * an action the workflow's current state doesn't allow, such as approving before the generated
 * process reached the editor or retrying a task that hasn't failed (409). Handled by
 * {@link GlobalExceptionHandler}.
 */
public class RecipeAiWorkflowException extends ApiException {

    public RecipeAiWorkflowException(HttpStatus status, String message) {
        super(message, status, null,
                status.is4xxClientError() && status != HttpStatus.BAD_REQUEST ? Outcome.REJECTED : Outcome.CLIENT_ERROR);
    }

    public static RecipeAiWorkflowException badRequest(String message) {
        return new RecipeAiWorkflowException(HttpStatus.BAD_REQUEST, message);
    }

    public static RecipeAiWorkflowException conflict(String message) {
        return new RecipeAiWorkflowException(HttpStatus.CONFLICT, message);
    }
}
