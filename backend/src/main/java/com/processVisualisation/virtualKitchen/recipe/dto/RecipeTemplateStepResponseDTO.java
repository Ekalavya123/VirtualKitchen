package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeTemplateStepResponseDTO {

    private Long id;
    private Long processTemplateId;
    private Long stepDefinitionId;
    private int stepOrder;
}
