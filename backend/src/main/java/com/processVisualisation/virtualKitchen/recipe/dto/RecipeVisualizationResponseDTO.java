package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response payload returned after triggering AI-generated visualization images for
 * a recipe, containing a status message and the per-step visualization results.
 */
@Data
@Builder
public class RecipeVisualizationResponseDTO {
    private String recipeId;
    private String message;
    private List<RecipeVisualizationStepResponseDTO> steps;
}
