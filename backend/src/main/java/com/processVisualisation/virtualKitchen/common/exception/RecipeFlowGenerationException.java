package com.processVisualisation.virtualKitchen.common.exception;

public class RecipeFlowGenerationException extends RuntimeException {

    public RecipeFlowGenerationException(String message) {
        super(message);
    }

    public RecipeFlowGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
