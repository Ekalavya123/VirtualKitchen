package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for adding a step to a recipe template, linking a step
 * definition to a template at a specific position in the recipe's step order.
 */
@Data
public class RecipeTemplateStepRequestDTO {

    @NotNull
    private Long processTemplateId;

    @NotNull
    private Long stepDefinitionId;

    @NotNull
    private Integer stepOrder;
}
