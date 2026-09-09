package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

/**
 * Request payload for starting a new recipe execution from a recipe template,
 * identifying the template, the initiating user and the kitchen it runs in.
 */
@Data
public class RecipeExecutionRequestDTO {

    private Long processTemplateId;
    private Long userId;
    private Long kitchenId;
}
