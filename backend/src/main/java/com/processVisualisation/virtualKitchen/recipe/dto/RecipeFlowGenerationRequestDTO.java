package com.processVisualisation.virtualKitchen.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecipeFlowGenerationRequestDTO {

    @NotBlank
    private String recipe;
}

