package com.processVisualisation.virtualKitchen.recipe.model;

/**
 * Lifecycle status of a {@link RecipeExecution} as a recipe is run in a kitchen,
 * from creation through completion or cancellation.
 */
public enum RecipeStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE,
    CANCELLED
}
