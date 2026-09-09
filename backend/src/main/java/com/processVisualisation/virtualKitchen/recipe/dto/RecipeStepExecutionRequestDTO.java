package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

/**
 * Request payload for starting the execution of a single step within a recipe
 * execution, referencing the parent execution and the step definition being run.
 */
@Data
public class RecipeStepExecutionRequestDTO {

    private Long processExecutionId;
    private Long stepDefinitionId;
}
