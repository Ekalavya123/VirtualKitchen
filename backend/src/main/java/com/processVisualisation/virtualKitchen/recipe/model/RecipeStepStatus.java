package com.processVisualisation.virtualKitchen.recipe.model;

/**
 * Lifecycle status of a single {@link RecipeStepExecution} within a recipe
 * execution, tracking that step's progress independently of the overall recipe.
 */
public enum RecipeStepStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE
}
