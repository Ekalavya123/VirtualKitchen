package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeVisualizationResponseDTO {
    private String recipeId;
    private String message;
    private List<RecipeVisualizationStepResponseDTO> steps;
}
