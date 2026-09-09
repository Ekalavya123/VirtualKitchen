package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response payload representing a persisted link between a recipe template and one
 * of its step definitions, including the step's position in the template's order.
 */
@Data
@Builder
public class RecipeTemplateStepResponseDTO {

    private Long id;
    private Long processTemplateId;
    private Long stepDefinitionId;
    private int stepOrder;
}
