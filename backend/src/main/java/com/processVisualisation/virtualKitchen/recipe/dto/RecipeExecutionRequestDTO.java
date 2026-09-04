package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

@Data
public class RecipeExecutionRequestDTO {

    private Long processTemplateId;
    private Long userId;
    private Long kitchenId;
}
