package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class RecipeTemplateStepRequestDTO {

    @NotNull
    private Long processTemplateId;

    @NotNull
    private Long stepDefinitionId;

    @NotNull
    private Integer stepOrder;
}
