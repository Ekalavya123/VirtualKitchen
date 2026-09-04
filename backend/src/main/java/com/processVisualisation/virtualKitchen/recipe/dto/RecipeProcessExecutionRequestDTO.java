package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

@Data
public class RecipeProcessExecutionRequestDTO {

    private Long processTemplateId;
    private Long userId;
    private Long kitchenId;
}
