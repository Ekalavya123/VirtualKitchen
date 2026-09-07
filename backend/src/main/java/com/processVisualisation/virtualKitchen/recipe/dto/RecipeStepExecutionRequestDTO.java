package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

@Data
public class RecipeStepExecutionRequestDTO {

    private Long processExecutionId;
    private Long stepDefinitionId;
}
