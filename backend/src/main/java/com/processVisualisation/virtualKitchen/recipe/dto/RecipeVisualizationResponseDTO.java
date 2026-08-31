package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RecipeVisualizationResponseDTO {
    private String recipeId;
    private String message;
    private List<RecipeVisualizationStepResponseDTO> steps;
}
